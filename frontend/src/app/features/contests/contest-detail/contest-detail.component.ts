import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { TokenService } from '../../../core/services/token.service';
import { ContestDetail } from '../../../core/models/contest.model';
import { StandingsLiveComponent } from '../standings-live/standings-live.component';

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
  imports: [CommonModule, RouterModule, StandingsLiveComponent],
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

  /** Has the contest finished? Ended contests are open to everyone, registered or not. */
  get ended(): boolean {
    return !!this.contest && Date.now() > Date.parse(this.contest.endsAt);
  }

  /** Has it started? Registering early buys no head start, so the prompt says so. */
  get started(): boolean {
    return !!this.contest && Date.now() >= Date.parse(this.contest.startsAt);
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
