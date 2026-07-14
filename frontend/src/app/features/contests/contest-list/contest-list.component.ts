import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { ContestSummary } from '../../../core/models/contest.model';

@Component({
  selector: 'app-contest-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './contest-list.component.html',
  styleUrls: ['./contest-list.component.scss']
})
export class ContestListComponent implements OnInit {
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

  /** Running / Upcoming / Ended, relative to now — a small at-a-glance status pill. */
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
