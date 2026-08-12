import { Component, inject, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { ApiService } from '../../../core/services/api.service';
import { WsService } from '../../../core/services/ws.service';
import { TokenService } from '../../../core/services/token.service';
import { ContestProblem } from '../../../core/models/contest.model';
import { Standings } from '../../../core/models/standings.model';
import { StandingsBoardComponent } from '../standings-board/standings-board.component';

/**
 * Owns a contest's standings: the initial snapshot, the live WS stream, and the freeze merge rule.
 * The board below it stays presentational.
 *
 * <p>Live standings use two channels. Everyone subscribes the broadcast topic (frozen board during a
 * freeze, live otherwise). Privileged viewers (ADMIN/PROBLEM_SETTER) ALSO subscribe the per-user
 * queue, which carries the live board during a freeze. Merge rule: a privileged viewer renders every
 * user-queue frame and IGNORES a frozen topic frame (its live version arrives on the queue);
 * everyone else renders every topic frame. The initial board comes from the role-aware GET snapshot.
 *
 * <p>Extracted so the contest page and the standings page cannot drift apart on this rule — a
 * second copy is how a frozen board eventually leaks on one of them.
 */
@Component({
  selector: 'app-standings-live',
  standalone: true,
  imports: [CommonModule, StandingsBoardComponent],
  template: `
    <app-standings-board
      [standings]="standings"
      [problems]="problems"
      [myHandle]="myHandle">
    </app-standings-board>
  `
})
export class StandingsLiveComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private ws = inject(WsService);
  private tokenService = inject(TokenService);

  /** Contest whose board this is. Set once, at creation. */
  @Input({ required: true }) contestId!: number;

  /** Problem slots, for the board's columns. */
  @Input() problems: ContestProblem[] = [];

  standings?: Standings;

  private subs: Subscription[] = [];

  get isPrivileged(): boolean {
    const role = this.tokenService.getUser()?.role;
    return !!role && role !== 'USER';
  }

  get myHandle(): string | null {
    return this.tokenService.getUser()?.username ?? null;
  }

  ngOnInit(): void {
    this.api.getStandings(this.contestId).subscribe({ next: (s) => (this.standings = s) });

    this.subs.push(
      this.ws.standings$(this.contestId).subscribe((board: Standings) => {
        // Privileged viewers get the live board on the user queue; skip the frozen topic frame.
        if (this.isPrivileged && board.frozen) {
          return;
        }
        this.standings = board;
      })
    );

    if (this.isPrivileged) {
      this.subs.push(
        this.ws.liveStandings$(this.contestId).subscribe((board: Standings) => (this.standings = board))
      );
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
  }
}
