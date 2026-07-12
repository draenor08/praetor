import { Component, inject, OnDestroy, OnInit } from '@angular/core';
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

  problem?: ProblemDetail;
  loadError = '';

  // Editor state. Monaco is deferred; a monospace <textarea> submits code fine.
  language = 'CPP';
  sourceCode = '';

  // Live judging state for the just-submitted attempt.
  submitting = false;
  submitError = '';
  submissionId?: number;
  liveStatus = '';
  liveVerdict: string | null = null;

  private liveSub?: Subscription;

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug')!;
    this.api.getProblem(slug).subscribe({
      next: (p) => (this.problem = p),
      error: () => (this.loadError = 'Could not load this problem.')
    });
  }

  ngOnDestroy(): void {
    this.liveSub?.unsubscribe();
  }

  get done(): boolean {
    return this.liveStatus === 'DONE';
  }

  submit(): void {
    if (!this.problem || !this.sourceCode.trim() || this.submitting) {
      return;
    }
    this.submitting = true;
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
            }
          });
        },
        error: (err) => {
          this.submitting = false;
          this.submitError = err?.error?.error ?? err?.error?.message ?? 'Submission failed.';
        }
      });
  }
}
