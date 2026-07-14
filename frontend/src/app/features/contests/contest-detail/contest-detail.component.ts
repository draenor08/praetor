import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../../../core/services/api.service';
import { WsService } from '../../../core/services/ws.service';
import { TokenService } from '../../../core/services/token.service';
import { ContestDetail } from '../../../core/models/contest.model';
import { Standings } from '../../../core/models/standings.model';
import { StandingsBoardComponent } from '../standings-board/standings-board.component';

/**
 * Contest page: meta + problems + register + the live ICPC standings board.
 *
 * <p>Live standings use two channels. Everyone subscribes the broadcast topic (frozen board during a
 * freeze, live otherwise). Privileged viewers (ADMIN/PROBLEM_SETTER) ALSO subscribe the per-user
 * queue, which carries the live board during a freeze. Merge rule: a privileged viewer renders every
 * user-queue frame and IGNORES a frozen topic frame (its live version arrives on the queue);
 * everyone else renders every topic frame. The initial board comes from the role-aware GET snapshot.
 */
@Component({
  selector: 'app-contest-detail',
  standalone: true,
  imports: [CommonModule, StandingsBoardComponent],
  templateUrl: './contest-detail.component.html',
  styleUrls: ['./contest-detail.component.scss']
})
export class ContestDetailComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private ws = inject(WsService);
  private tokenService = inject(TokenService);
  private route = inject(ActivatedRoute);

  private id!: number;
  private subs: Subscription[] = [];

  contest?: ContestDetail;
  standings?: Standings;
  loading = true;
  error = '';

  registering = false;
  registerMsg = '';

  get isPrivileged(): boolean {
    const role = this.tokenService.getUser()?.role;
    return !!role && role !== 'USER';
  }

  get myHandle(): string | null {
    return this.tokenService.getUser()?.username ?? null;
  }

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));

    this.api.getContest(this.id).subscribe({
      next: (c) => {
        this.contest = c;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load this contest.';
        this.loading = false;
      }
    });

    // Initial board (role-aware on the backend) + live updates.
    this.api.getStandings(this.id).subscribe({ next: (s) => (this.standings = s) });

    this.subs.push(
      this.ws.standings$(this.id).subscribe((board: Standings) => {
        // Privileged viewers get the live board on the user queue; skip the frozen topic frame.
        if (this.isPrivileged && board.frozen) {
          return;
        }
        this.standings = board;
      })
    );

    if (this.isPrivileged) {
      this.subs.push(
        this.ws.liveStandings$(this.id).subscribe((board: Standings) => (this.standings = board))
      );
    }
  }

  register(): void {
    if (this.registering) {
      return;
    }
    this.registering = true;
    this.registerMsg = '';
    this.api.registerForContest(this.id).subscribe({
      next: () => {
        this.registering = false;
        this.registerMsg = 'Registered ✓';
      },
      error: (err) => {
        this.registering = false;
        this.registerMsg = err?.status === 409 ? 'Already registered' : 'Registration failed';
      }
    });
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
  }
}
