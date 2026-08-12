import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { ContestSummary } from '../../../core/models/contest.model';

/**
 * Standings entry point: the contests, each linking to its own board. Standings are per-contest, so
 * the rail item lands here rather than on a board that would have to guess which contest is meant.
 */
@Component({
  selector: 'app-standings-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './standings-list.component.html',
  styleUrls: ['./standings-list.component.scss']
})
export class StandingsListComponent implements OnInit {
  private api = inject(ApiService);

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

  /** Running / Upcoming / Ended, relative to now — the same pill the contest list shows. */
  status(c: ContestSummary): 'Running' | 'Upcoming' | 'Ended' {
    const now = Date.now();
    if (now < Date.parse(c.startsAt)) {
      return 'Upcoming';
    }
    if (now > Date.parse(c.endsAt)) {
      return 'Ended';
    }
    return 'Running';
  }
}
