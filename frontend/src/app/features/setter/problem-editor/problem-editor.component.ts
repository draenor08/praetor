import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { ProblemInput } from '../../../core/models/problem.model';

const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/**
 * Create / edit one problem.
 *
 * <p>The validation below is a deliberate mirror of `ProblemService.validate` — same bounds, same
 * conditional requirement (floatEps for FLOAT). The point is that the setter sees the problem with
 * their input before a request goes out, instead of decoding a 400. The server still validates;
 * this only removes the round trip.
 *
 * <p>SPECIAL is absent from `judgeModes` on purpose: the engine has no custom-checker runner, so
 * the backend rejects that mode outright. Offering it here would only produce a guaranteed 400.
 *
 * <p>While a contest containing the problem is running, the fields that define how it is judged
 * are locked, because the backend refuses to change them mid-contest. Prose stays editable so
 * typo fixes and clarifications still work during a round.
 */
@Component({
  selector: 'app-problem-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './problem-editor.component.html',
  styleUrls: ['./problem-editor.component.scss']
})
export class ProblemEditorComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly judgeModes = ['EXACT', 'TOKEN', 'FLOAT'];

  /** null while creating; the original slug while editing (the slug itself is editable). */
  originalSlug: string | null = null;

  form: ProblemInput = {
    slug: '',
    title: '',
    statement: '',
    constraints: '',
    difficulty: 800,
    timeLimitMs: 1000,
    memLimitKb: 262144,
    judgeMode: 'EXACT',
    floatEps: null,
    checkerCode: '',
    editorial: '',
    draft: false
  };

  loading = false;
  saving = false;
  loadError = '';
  saveError = '';
  /** Field name → message, keyed the same as the form fields. */
  errors: Record<string, string> = {};

  /** Judging fields are frozen while a contest using this problem is running. */
  judgingLocked = false;
  archived = false;

  get editing(): boolean {
    return this.originalSlug !== null;
  }

  /** What the backend will actually store, so the setter can see it before saving. */
  get normalizedSlug(): string {
    return (this.form.slug ?? '').trim().toLowerCase();
  }

  get memLimitMb(): number {
    return Math.round((this.form.memLimitKb / 1024) * 10) / 10;
  }

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (!slug) {
      return;
    }
    this.originalSlug = slug;
    this.loading = true;

    this.api.getManagedProblem(slug).subscribe({
      next: (problem) => {
        this.form = {
          slug: problem.slug,
          title: problem.title,
          statement: problem.statement,
          constraints: problem.constraints ?? '',
          difficulty: problem.difficulty,
          timeLimitMs: problem.timeLimitMs,
          memLimitKb: problem.memLimitKb,
          judgeMode: problem.judgeMode,
          floatEps: problem.floatEps,
          checkerCode: problem.checkerCode ?? '',
          editorial: problem.editorial ?? ''
        };
        this.archived = problem.archived;
        this.loading = false;
      },
      error: (err) => {
        this.loadError = err?.error?.error ?? 'Could not load this problem.';
        this.loading = false;
      }
    });

    // Separate call: it answers "may these fields be edited right now", not "what are they".
    this.api.getProblemUsage(slug).subscribe({
      next: (usage) => (this.judgingLocked = usage.inLiveContest),
      error: () => (this.judgingLocked = false)
    });
  }

  /** Mirrors ProblemService.validate. Returns true when the form is safe to send. */
  private validate(): boolean {
    const e: Record<string, string> = {};
    const slug = this.normalizedSlug;

    if (!slug) {
      e['slug'] = 'Slug is required.';
    } else if (!SLUG_PATTERN.test(slug)) {
      e['slug'] = 'Lowercase letters, numbers and single hyphens only — e.g. a-plus-b.';
    } else if (slug.length > 80) {
      e['slug'] = 'At most 80 characters.';
    }

    if (!this.form.title?.trim()) {
      e['title'] = 'Title is required.';
    } else if (this.form.title.trim().length > 200) {
      e['title'] = 'At most 200 characters.';
    }

    if (!this.form.statement?.trim()) {
      e['statement'] = 'Statement is required.';
    }

    if (this.form.difficulty === null || this.form.difficulty < 0 || this.form.difficulty > 4000) {
      e['difficulty'] = 'Between 0 and 4000.';
    }

    if (!this.form.timeLimitMs || this.form.timeLimitMs < 1) {
      e['timeLimitMs'] = 'At least 1 ms.';
    }

    if (!this.form.memLimitKb || this.form.memLimitKb < 1) {
      e['memLimitKb'] = 'At least 1 KB.';
    }

    if (this.form.judgeMode === 'FLOAT' && !(Number(this.form.floatEps) > 0)) {
      e['floatEps'] = 'FLOAT judging needs a tolerance greater than 0.';
    }

    this.errors = e;
    return Object.keys(e).length === 0;
  }

  save(): void {
    this.saveError = '';
    if (!this.validate() || this.saving) {
      return;
    }
    this.saving = true;

    // Send only what the chosen mode uses, so a leftover tolerance from a mode the setter tried
    // and abandoned is not persisted.
    const body: ProblemInput = {
      ...this.form,
      slug: this.normalizedSlug,
      title: this.form.title.trim(),
      constraints: this.form.constraints?.trim() ? this.form.constraints : null,
      editorial: this.form.editorial?.trim() ? this.form.editorial : null,
      floatEps: this.form.judgeMode === 'FLOAT' ? Number(this.form.floatEps) : null,
      checkerCode: null,
      // Only meaningful at creation: publication is one-way, so an existing problem's draft
      // status is never changed from here.
      draft: this.editing ? null : this.form.draft
    };

    const request = this.editing
      ? this.api.updateProblem(this.originalSlug!, body)
      : this.api.createProblem(body);

    request.subscribe({
      next: (saved) => {
        this.saving = false;
        // A new problem is unjudgeable until it has test cases, so go straight there.
        if (this.editing) {
          this.router.navigate(['/setter/problems']);
        } else {
          this.router.navigate(['/setter/problems', saved.slug, 'testcases']);
        }
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err?.error?.error ?? err?.error?.message ?? 'Could not save this problem.';
      }
    });
  }
}
