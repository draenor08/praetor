import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../../core/services/api.service';
import { ContestSummary, EligibleProblem, Proposal } from '../../../core/models/contest.model';
import { markDirty } from '../../../core/rx/mark-dirty';

/**
 * A setter's side of contest authoring: which contests are asking for problems, which of their
 * drafts can still be offered, and what became of the offers already made.
 *
 * <p>Only draft problems can be offered, and the pool shown here is the same one the admin sees —
 * a problem that has ever been publicly visible is spent. That is why the page says so plainly
 * rather than silently offering a short list.
 */
@Component({
  selector: 'app-contest-calls',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './contest-calls.component.html',
  styleUrls: ['./contest-calls.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ContestCallsComponent implements OnInit {
  private cdr = inject(ChangeDetectorRef);
  private api = inject(ApiService);

  openContests: ContestSummary[] = [];
  pool: EligibleProblem[] = [];
  mine: Proposal[] = [];

  loading = true;
  error = '';
  notice = '';
  busy = false;

  /** The contest currently being offered to, keyed for the inline form. */
  offeringTo: number | null = null;
  chosenProblemId: number | null = null;
  note = '';

  ngOnInit(): void {
    this.load();
  }

  startOffer(contestId: number): void {
    this.offeringTo = contestId;
    this.chosenProblemId = this.pool.length > 0 ? this.pool[0].problemId : null;
    this.note = '';
    this.error = '';
  }

  cancelOffer(): void {
    this.offeringTo = null;
    this.chosenProblemId = null;
  }

  submitOffer(): void {
    if (this.busy || this.offeringTo === null || this.chosenProblemId === null) {
      return;
    }
    this.busy = true;
    this.error = '';
    this.notice = '';
    this.api.proposeProblem(this.offeringTo, this.chosenProblemId, this.note.trim() || null).pipe(markDirty(this.cdr)).subscribe({
      next: (p) => {
        this.busy = false;
        this.offeringTo = null;
        this.notice = `${p.title} offered. The admin decides whether it goes in.`;
        this.load();
      },
      error: (err) => {
        this.busy = false;
        const e = err as { error?: { error?: string; message?: string } };
        this.error = e?.error?.error || e?.error?.message || 'Could not send that proposal.';
      }
    });
  }

  /** Drafts not already offered to the contest being offered to. */
  get offerable(): EligibleProblem[] {
    if (this.offeringTo === null) {
      return this.pool;
    }
    const already = new Set(
      this.mine.filter((p) => p.contestId === this.offeringTo).map((p) => p.problemId)
    );
    return this.pool.filter((p) => !already.has(p.problemId));
  }

  private load(): void {
    forkJoin({
      contests: this.api.getContests(),
      pool: this.api.getEligibleProblems(),
      mine: this.api.getMyProposals()
    }).pipe(markDirty(this.cdr)).subscribe({
      next: ({ contests, pool, mine }) => {
        this.openContests = contests.filter((c) => c.callsOpen);
        this.pool = pool;
        this.mine = mine;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load the calls for problems.';
        this.loading = false;
      }
    });
  }
  /** Keyed so a refresh reorders rows instead of rebuilding every one. */
  trackCall(_index: number, c: any): any {
    return c.id;
  }

  /** Keyed so a refresh reorders rows instead of rebuilding every one. */
  trackMine(_index: number, p: any): any {
    return p.id;
  }

}
