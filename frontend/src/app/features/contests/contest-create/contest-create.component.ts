import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { EligibleProblem } from '../../../core/models/contest.model';
import { markDirty } from '../../../core/rx/mark-dirty';

/**
 * Contest authoring, for an admin. Meta plus a problem set drawn from the eligible pool, and the
 * option to open the contest for setter proposals instead of (or as well as) picking now.
 *
 * <p>The pool only ever contains drafts: a contest may use a problem nobody has been able to read.
 * Problems already published, or already claimed by another contest, are not offered here at all —
 * the backend refuses them too, so this list is a convenience, not the rule.
 */
@Component({
  selector: 'app-contest-create',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './contest-create.component.html',
  styleUrls: ['./contest-create.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ContestCreateComponent implements OnInit {
  private cdr = inject(ChangeDetectorRef);
  private api = inject(ApiService);
  private router = inject(Router);

  title = '';
  startsAt = '';
  endsAt = '';
  freezeMin = 15;
  scoring = 'ICPC';
  callsOpen = false;

  pool: EligibleProblem[] = [];
  /** problemId → label. Presence in this map is what "picked" means. */
  picked = new Map<number, string>();

  loading = true;
  saving = false;
  error = '';
  fieldErrors: Record<string, string> = {};

  ngOnInit(): void {
    this.api.getEligibleProblems().pipe(markDirty(this.cdr)).subscribe({
      next: (pool) => {
        this.pool = pool;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load the problem pool.';
        this.loading = false;
      }
    });
  }

  isPicked(p: EligibleProblem): boolean {
    return this.picked.has(p.problemId);
  }

  /** Toggle a problem in or out, relabelling the rest so labels stay A, B, C… with no gaps. */
  toggle(p: EligibleProblem): void {
    if (this.picked.has(p.problemId)) {
      this.picked.delete(p.problemId);
    } else {
      this.picked.set(p.problemId, '');
    }
    this.relabel();
  }

  label(p: EligibleProblem): string {
    return this.picked.get(p.problemId) ?? '';
  }

  get pickedCount(): number {
    return this.picked.size;
  }

  save(): void {
    if (this.saving) {
      return;
    }
    this.fieldErrors = {};
    this.error = '';

    if (!this.title.trim()) {
      this.fieldErrors['title'] = 'Title is required.';
    }
    if (!this.startsAt) {
      this.fieldErrors['startsAt'] = 'Start time is required.';
    }
    if (!this.endsAt) {
      this.fieldErrors['endsAt'] = 'End time is required.';
    }
    if (this.startsAt && this.endsAt && Date.parse(this.endsAt) <= Date.parse(this.startsAt)) {
      this.fieldErrors['endsAt'] = 'End must be after start.';
    }
    if (this.freezeMin < 0) {
      this.fieldErrors['freezeMin'] = 'Freeze cannot be negative.';
    }
    // Mirrors the backend: an empty contest is only meaningful if setters are about to fill it.
    if (this.picked.size === 0 && !this.callsOpen) {
      this.fieldErrors['problems'] =
        'Pick at least one problem, or open the contest for setter proposals.';
    }
    if (Object.keys(this.fieldErrors).length > 0) {
      return;
    }

    this.saving = true;
    this.api
      .createContest({
        title: this.title.trim(),
        startsAt: new Date(this.startsAt).toISOString(),
        endsAt: new Date(this.endsAt).toISOString(),
        freezeMin: this.freezeMin,
        scoring: this.scoring,
        problems: [...this.picked.entries()].map(([problemId, label], i) => ({
          problemId,
          label,
          ord: i + 1
        })),
        callsOpen: this.callsOpen
      })
      .pipe(markDirty(this.cdr)).subscribe({
        next: (contest) => {
          this.saving = false;
          this.router.navigate(['/contests', contest.id]);
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.error || err?.error?.message || 'Could not create the contest.';
        }
      });
  }

  /** Labels follow pick order: A, B, C… Re-derived on every change so removals leave no hole. */
  private relabel(): void {
    const ids = [...this.picked.keys()];
    ids.forEach((id, i) => this.picked.set(id, String.fromCharCode(65 + i)));
  }
  /** Keyed so a refresh reorders rows instead of rebuilding every one. */
  trackPoolProblem(_index: number, p: any): any {
    return p.problemId;
  }

}
