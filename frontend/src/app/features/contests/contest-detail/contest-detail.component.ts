import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { TokenService } from '../../../core/services/token.service';
import { ContestDetail } from '../../../core/models/contest.model';
import { StandingsLiveComponent } from '../standings-live/standings-live.component';
import { CountdownComponent } from '../../../shared/components/countdown/countdown.component';
import { phaseOf, serverNowMs, serverSkewMs } from '../../../shared/contest-clock';

/**
 * Contest page: meta, the problem set, registration, and the live ICPC standings board.
 *
 * <p>The problem set is only listed once the caller may actually open the statements — staff always,
 * a registered participant while the contest runs, anyone at all once it has ended. Until then the
 * backend withholds each slot's slug and title and this page shows a register prompt instead. The
 * gate is enforced server-side; this is the same rule stated in the UI, not the rule itself.
 */
@Component({
  selector: 'app-contest-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, StandingsLiveComponent, CountdownComponent],
  templateUrl: './contest-detail.component.html',
  styleUrls: ['./contest-detail.component.scss']
})
export class ContestDetailComponent implements OnInit {
  private api = inject(ApiService);
  private tokenService = inject(TokenService);
  private route = inject(ActivatedRoute);

  contestId!: number;
  contest?: ContestDetail;
  loading = true;
  error = '';

  registering = false;
  registerMsg = '';

  get isPrivileged(): boolean {
    const role = this.tokenService.getUser()?.role;
    return !!role && role !== 'USER';
  }

  /**
   * Server time now. Both phase getters below read it rather than `Date.now()`: they decide what the
   * page SAYS about a window the backend actually enforces, so a skewed browser clock would have the
   * page contradict the API it is talking to.
   */
  private get nowMs(): number {
    return serverNowMs(serverSkewMs(this.contest?.serverNow));
  }

  /** Has the contest finished? Ended contests are open to everyone, registered or not. */
  get ended(): boolean {
    return !!this.contest && phaseOf(this.contest.startsAt, this.contest.endsAt, this.nowMs) === 'Ended';
  }

  /** Has it started? Registering early buys no head start, so the prompt says so. */
  get started(): boolean {
    return !!this.contest && phaseOf(this.contest.startsAt, this.contest.endsAt, this.nowMs) !== 'Upcoming';
  }

  /** Registration is only worth offering to a contestant on a contest that has not ended. */
  get canRegister(): boolean {
    return !!this.contest && !this.contest.registered && !this.ended && !this.isPrivileged;
  }

  /** Why the problems are withheld — shown in the prompt so the state is not a mystery. */
  get lockReason(): string {
    if (!this.contest) {
      return '';
    }
    if (!this.tokenService.getUser()) {
      return 'Log in and register to see this contest’s problems.';
    }
    if (this.contest.registered && !this.started) {
      return 'You are registered. The problems appear when the contest starts.';
    }
    return 'Register to see this contest’s problems while it runs. They open to everyone once it ends.';
  }

  ngOnInit(): void {
    this.contestId = Number(this.route.snapshot.paramMap.get('id'));

    this.load();
  }

  /**
   * The countdown hit a boundary — the contest just started, or just ended. What that changes
   * (whether the problem slots carry slugs now) is decided server-side, so re-fetch instead of
   * flipping anything locally. Without this, a participant watching the clock reach zero would sit
   * on a page that still says the problems are withheld.
   */
  onPhaseChange(): void {
    this.load();
  }

  register(): void {
    if (this.registering) {
      return;
    }
    this.registering = true;
    this.registerMsg = '';
    this.api.registerForContest(this.contestId).subscribe({
      next: () => {
        this.registering = false;
        this.registerMsg = 'Registered ✓';
        // Re-fetch rather than flip the flag locally: whether the problems are now visible is the
        // backend's call (a contest that has not started yet still withholds them).
        this.load();
      },
      error: (err) => {
        this.registering = false;
        this.registerMsg = err?.status === 409 ? 'Already registered' : 'Registration failed';
        if (err?.status === 409) {
          this.load();
        }
      }
    });
  }

  private load(): void {
    this.api.getContest(this.contestId).subscribe({
      next: (c) => {
        this.contest = c;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load this contest.';
        this.loading = false;
      }
    });
  }
}
