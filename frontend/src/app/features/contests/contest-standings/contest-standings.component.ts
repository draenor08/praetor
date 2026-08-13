import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { ContestDetail } from '../../../core/models/contest.model';
import { StandingsLiveComponent } from '../standings-live/standings-live.component';
import { CountdownComponent } from '../../../shared/components/countdown/countdown.component';

/**
 * One contest's standings on its own page, reached from the Standings section. The contest is
 * fetched for its title and problem labels; the board itself — snapshot, live stream, freeze rule —
 * belongs to {@link StandingsLiveComponent}, shared with the contest page.
 */
@Component({
  selector: 'app-contest-standings',
  standalone: true,
  imports: [CommonModule, RouterModule, StandingsLiveComponent, CountdownComponent],
  templateUrl: './contest-standings.component.html',
  styleUrls: ['./contest-standings.component.scss']
})
export class ContestStandingsComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);

  contestId!: number;
  contest?: ContestDetail;
  loading = true;
  error = '';

  ngOnInit(): void {
    this.contestId = Number(this.route.snapshot.paramMap.get('id'));

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
