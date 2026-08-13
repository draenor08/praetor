import { Component, ElementRef, NgZone, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../../../core/services/api.service';
import { WsService } from '../../../core/services/ws.service';
import { ProblemDetail } from '../../../core/models/problem.model';

@Component({
  selector: 'app-problem-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './problem-detail.component.html',
  styleUrls: ['./problem-detail.component.scss']
})
export class ProblemDetailComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private ws = inject(WsService);
  private route = inject(ActivatedRoute);
  private zone = inject(NgZone);

  // The editor host lives inside *ngIf="problem" (loaded async), so it enters the DOM after this
  // component's initial view init. A ViewChild SETTER inits Monaco exactly when the host appears.
  @ViewChild('editorHost')
  set editorHost(host: ElementRef<HTMLElement> | undefined) {
    if (host && !this.editor) {
      void this.initEditor(host.nativeElement);
    }
  }
  // Monaco is loaded at runtime via its self-contained AMD build under assets/ (see loadMonaco),
  // NOT bundled through esbuild — that avoids the font/worker bundling issues and keeps monaco
  // out of the app bundle entirely. Typed `any` since the ESM types aren't imported.
  private editor?: any;
  private monaco?: any;
  private static monacoLoading?: Promise<any>;

  problem?: ProblemDetail;
  loadError = '';

  // Editor state. Monaco fills #editorHost; `sourceCode` stays the single source submit() reads.
  language = 'CPP';
  sourceCode = '';

  // Live judging state for the just-submitted attempt.
  submitting = false;
  submitError = '';
  submissionId?: number;
  liveStatus = '';
  liveVerdict: string | null = null;

  // Rate-limit cooldown: seconds left before the server will accept another submission.
  cooldownSeconds = 0;
  private cooldownTimer?: ReturnType<typeof setInterval>;

  private liveSub?: Subscription;

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug')!;
    this.api.getProblem(slug).subscribe({
      next: (p) => (this.problem = p),
      error: () => (this.loadError = 'Could not load this problem.')
    });
  }

  /** Load Monaco's AMD build from assets once (offline; nginx serves it). Idempotent + shared. */
  private loadMonaco(): Promise<any> {
    const w = window as any;
    if (w.monaco) {
      return Promise.resolve(w.monaco);
    }
    if (ProblemDetailComponent.monacoLoading) {
      return ProblemDetailComponent.monacoLoading;
    }
    // Absolute (origin-root) path — a relative 'assets/...' would resolve against the current
    // route (e.g. /problems/<slug>) and hit the SPA fallback (index.html) instead of the script.
    const base = '/assets/monaco/vs';
    ProblemDetailComponent.monacoLoading = new Promise<any>((resolve, reject) => {
      // Worker runs from a data-URI proxy that pulls monaco's workerMain from assets (offline).
      w.MonacoEnvironment = {
        getWorkerUrl: () =>
          'data:text/javascript;charset=utf-8,' +
          encodeURIComponent(
            `self.MonacoEnvironment={baseUrl:'${location.origin}/assets/monaco/'};` +
              `importScripts('${location.origin}${base}/base/worker/workerMain.js');`
          )
      };
      const loader = document.createElement('script');
      loader.src = `${base}/loader.js`;
      loader.onload = () => {
        w.require.config({ paths: { vs: base } });
        w.require(['vs/editor/editor.main'], () => resolve(w.monaco));
      };
      loader.onerror = reject;
      document.body.appendChild(loader);
    });
    return ProblemDetailComponent.monacoLoading;
  }

  /** Monaco's grammar id for the selected language; C++ is the fallback and the default. */
  private monacoLang(): string {
    switch (this.language) {
      case 'PYTHON':
        return 'python';
      case 'JAVA':
        return 'java';
      default:
        return 'cpp';
    }
  }

  /** Switch the editor's syntax highlighting when the language select changes. */
  onLanguageChange(): void {
    const model = this.editor?.getModel();
    if (model && this.monaco) {
      this.monaco.editor.setModelLanguage(model, this.monacoLang());
    }
  }

  private async initEditor(host: HTMLElement): Promise<void> {
    const monaco = await this.loadMonaco();
    this.monaco = monaco;
    this.editor = monaco.editor.create(host, {
      value: this.sourceCode,
      language: this.monacoLang(),
      theme: 'vs-dark',
      automaticLayout: true, // internal ResizeObserver — no manual resize handling needed
      minimap: { enabled: false },
      fontSize: 14,
      scrollBeyondLastLine: false,
      readOnly: this.submitting
    });
    // Run the assignment through NgZone so the Submit button's [disabled]="!sourceCode.trim()"
    // updates on every keystroke regardless of Monaco's callback zone.
    this.editor.onDidChangeModelContent(() => {
      const value = this.editor.getValue();
      this.zone.run(() => (this.sourceCode = value));
    });
  }

  private setEditorReadOnly(readOnly: boolean): void {
    this.editor?.updateOptions({ readOnly });
  }

  ngOnDestroy(): void {
    this.liveSub?.unsubscribe();
    this.editor?.dispose();
    this.clearCooldown();
  }

  get done(): boolean {
    return this.liveStatus === 'DONE';
  }

  submit(): void {
    if (!this.problem || !this.sourceCode.trim() || this.submitting) {
      return;
    }
    this.submitting = true;
    this.setEditorReadOnly(true);
    this.submitError = '';
    this.liveVerdict = null;
    this.liveStatus = '';
    this.liveSub?.unsubscribe();

    this.api.submit({ problemSlug: this.problem.slug, language: this.language, sourceCode: this.sourceCode })
      .subscribe({
        next: (created) => {
          this.submissionId = created.id;
          this.liveStatus = created.status;
          // Subscribe AFTER we have the id; the shared client resubscribes on (re)connect.
          this.liveSub = this.ws.submission$(created.id).subscribe((ev) => {
            this.liveStatus = ev.status;
            this.liveVerdict = ev.verdict;
            if (ev.status === 'DONE') {
              this.submitting = false; // re-enable submit once judging finishes
              this.setEditorReadOnly(false);
            }
          });
        },
        error: (err) => {
          this.submitting = false;
          this.setEditorReadOnly(false);
          if (err?.status === 429) {
            this.startCooldown(err);
            return;
          }
          this.submitError = err?.error?.error ?? err?.error?.message ?? 'Submission failed.';
        }
      });
  }

  /**
   * 429 = the per-user submission cooldown. Rather than a dead error line, count the wait down
   * and keep Submit disabled until it reaches zero, so the button state always matches what the
   * server would actually accept.
   */
  private startCooldown(err: any): void {
    const fromBody = Number(err?.error?.retryAfterSec);
    const fromHeader = Number(err?.headers?.get?.('Retry-After'));
    const seconds = [fromBody, fromHeader].find((n) => Number.isFinite(n) && n > 0) ?? 10;

    this.clearCooldown();
    this.cooldownSeconds = Math.ceil(seconds);
    this.cooldownTimer = setInterval(() => {
      this.zone.run(() => {
        this.cooldownSeconds -= 1;
        if (this.cooldownSeconds <= 0) {
          this.clearCooldown();
        }
      });
    }, 1000);
  }

  private clearCooldown(): void {
    if (this.cooldownTimer !== undefined) {
      clearInterval(this.cooldownTimer);
      this.cooldownTimer = undefined;
    }
    this.cooldownSeconds = 0;
  }
}
