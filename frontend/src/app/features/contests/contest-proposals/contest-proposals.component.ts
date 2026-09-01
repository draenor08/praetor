import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { TokenService } from '../../../core/services/token.service';
import { ContestDetail, Proposal } from '../../../core/models/contest.model';
import { markDirty } from '../../../core/rx/mark-dirty';

/**
 * The admin's review queue for one contest: what setters have offered, and the decision.
 *
 * <p>Accepting needs a label, because that is what the problem will be called in the contest — it is
 * asked for here rather than assigned silently, since labels are what contestants and the standings
 * board refer to. Rejecting leaves the problem a draft, free to be offered elsewhere.
 */
@Component({
  selector: 'app-contest-proposals',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './contest-proposals.component.html',
  styleUrls: ['./contest-proposals.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ContestProposalsComponent implements OnInit {
  private cdr = inject(ChangeDetectorRef);
  private api = inject(ApiService);
  private tokenService = inject(TokenService);
  private route = inject(ActivatedRoute);

  contestId!: number;
  contest?: ContestDetail;
  proposals: Proposal[] = [];

  loading = true;
  error = '';
  notice = '';
  busyId: number | null = null;

  /** proposalId → the label typed for it. */
  labels: Record<number, string> = {};

  get isAdmin(): boolean {
    return this.tokenService.getUser()?.role === 'ADMIN';
  }

  get pending(): Proposal[] {
    return this.proposals.filter((p) => p.status === 'PROPOSED');
  }

  get decided(): Proposal[] {
    return this.proposals.filter((p) => p.status !== 'PROPOSED');
  }

  ngOnInit(): void {
    this.contestId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  /** Suggest the next free label, so the common case is one click and no typing. */
  suggestedLabel(index: number): string {
    const used = new Set((this.contest?.problems ?? []).map((p) => p.label));
    let code = 65;
    let taken = 0;
    while (taken <= index) {
      const candidate = String.fromCharCode(code);
      if (!used.has(candidate)) {
        if (taken === index) {
          return candidate;
        }
        taken++;
      }
      code++;
    }
    return '';
  }

  accept(p: Proposal, index: number): void {
    const label = (this.labels[p.id] || this.suggestedLabel(index)).trim().toUpperCase();
    if (!label) {
      this.error = 'A label is needed to accept a problem.';
      return;
    }
    this.act(p, this.api.acceptProposal(this.contestId, p.id, label), `${p.title} is in as ${label}.`);
  }

  reject(p: Proposal): void {
    this.act(p, this.api.rejectProposal(this.contestId, p.id), `${p.title} was turned down.`);
  }

  toggleCalls(): void {
    if (!this.contest) {
      return;
    }
    const next = !this.contest.callsOpen;
    this.api.setContestCalls(this.contestId, next).pipe(markDirty(this.cdr)).subscribe({
      next: (c) => {
        this.contest = c;
        this.notice = next ? 'Setters can now propose problems.' : 'Proposals are closed.';
      },
      error: (err) => (this.error = this.messageFrom(err))
    });
  }

  private act(p: Proposal, call: ReturnType<ApiService['rejectProposal']>, done: string): void {
    if (this.busyId !== null) {
      return;
    }
    this.busyId = p.id;
    this.error = '';
    this.notice = '';
    call.pipe(markDirty(this.cdr)).subscribe({
      next: () => {
        this.busyId = null;
        this.notice = done;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = this.messageFrom(err);
      }
    });
  }

  private load(): void {
    this.api.getContest(this.contestId).pipe(markDirty(this.cdr)).subscribe({
      next: (c) => (this.contest = c),
      error: () => (this.error = 'Could not load this contest.')
    });

    this.api.getProposals(this.contestId).pipe(markDirty(this.cdr)).subscribe({
      next: (list) => {
        this.proposals = list;
        this.loading = false;
      },
      error: (err) => {
        this.error = this.messageFrom(err);
        this.loading = false;
      }
    });
  }

  private messageFrom(err: unknown): string {
    const e = err as { error?: { error?: string; message?: string } };
    return e?.error?.error || e?.error?.message || 'Something went wrong.';
  }
  /** Keyed so a refresh reorders rows instead of rebuilding every one. */
  trackProposal(_index: number, p: any): any {
    return p.id;
  }

  /** Keyed so a refresh reorders rows instead of rebuilding every one. */
  trackPending(index: number, p: any): any {
    return p.id;
  }

}
