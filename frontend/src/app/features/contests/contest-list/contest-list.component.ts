import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { TokenService } from '../../../core/services/token.service';
import { ContestSummary } from '../../../core/models/contest.model';
import { CountdownComponent } from '../../../shared/components/countdown/countdown.component';
import { ContestPhase, phaseOf, serverNowMs, serverSkewMs } from '../../../shared/contest-clock';

@Component({
  selector: 'app-contest-list',
  standalone: true,
  imports: [CommonModule, RouterModule, CountdownComponent],
  templateUrl: './contest-list.component.html',
  styleUrls: ['./contest-list.component.scss']
})
export class ContestListComponent implements OnInit {
  private api = inject(ApiService);
  private tokenService = inject(TokenService);

  get isAdmin(): boolean {
    return this.tokenService.getUser()?.role === 'ADMIN';
  }

  /** Staff see which contests are collecting problems; contestants have no use for it. */
  get isStaff(): boolean {
    const role = this.tokenService.getUser()?.role;
    return !!role && role !== 'USER';
  }

  contests: ContestSummary[] = [];
  loading = true;
  error = '';

  ngOnInit(): void {
    this.api.getContests().subscribe({
      next: (list) => {
        this.contests = list;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load contests.';
        this.loading = false;
      }
    });
  }

  /**
   * Running / Upcoming / Ended — a small at-a-glance status pill, decided against the SERVER clock
   * the rows carry, so the pill cannot disagree with the countdown beside it (or with the backend).
   */
  status(c: ContestSummary): ContestPhase {
    return phaseOf(c.startsAt, c.endsAt, serverNowMs(serverSkewMs(c.serverNow)));
  }

  /** A contest crossed a boundary while the page was open — re-fetch so the pills stay honest. */
  onPhaseChange(): void {
    this.api.getContests().subscribe({ next: (list) => (this.contests = list) });
  }
}
