import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { ApiService } from '../../core/services/api.service';
import { UserRating } from '../../core/models/rating.model';
import { ProfileSolveStats } from '../../core/models/profile.model';

interface ChartPoint {
  x: number;
  y: number;
  rating: number;
  label: string;
}

/** Chart geometry, in viewBox units. The SVG scales to its container. */
const CHART_WIDTH = 640;
const CHART_HEIGHT = 180;
const PAD_X = 34;
const PAD_Y = 18;

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit {
  private authService = inject(AuthService);
  private api = inject(ApiService);

  user: any = null;
  rating?: UserRating;
  profileStats?: ProfileSolveStats | null = null;
  loading = true;
  error = '';

  /** contestId → title, so history reads as contest names rather than bare ids. */
  private contestTitles = new Map<number, string>();

  readonly chartWidth = CHART_WIDTH;
  readonly chartHeight = CHART_HEIGHT;

  ngOnInit(): void {
    this.authService.getMe().subscribe({
      next: (user) => {
        this.user = user;
        this.loading = false;
        this.loadRating(user?.username);
        this.loadProfileStats(user?.username);
      },
      error: () => {
        this.error = 'Could not load profile.';
        this.loading = false;
      }
    });

    // Cosmetic: a missing contest list just means history shows "Contest #id".
    this.api.getContests().subscribe({
      next: (contests) => contests.forEach((c) => this.contestTitles.set(c.id, c.title)),
      error: () => undefined
    });
  }

  private loadRating(handle?: string): void {
    if (!handle) {
      return;
    }
    // Rating lives on its own endpoint because it carries the live rank and full history;
    // /api/users/me only knows the current value.
    this.api.getUserRating(handle).subscribe({
      next: (rating) => (this.rating = rating),
      error: () => undefined
    });
  }

  private loadProfileStats(handle?: string): void {
    if (!handle) {
      return;
    }
    this.api.getUserSolveStats(handle).subscribe({
      next: (s) => (this.profileStats = s),
      error: () => undefined
    });
  }

  contestLabel(contestId: number): string {
    return this.contestTitles.get(contestId) ?? `Contest #${contestId}`;
  }

  delta(entry: { before: number; after: number }): number {
    return entry.after - entry.before;
  }

  /** History newest-first for reading; the chart keeps chronological order. */
  get historyDesc() {
    return [...(this.rating?.history ?? [])].reverse();
  }

  // --- chart --------------------------------------------------------------

  /**
   * Rating over time. The series starts at the `before` of the first change, so the line shows
   * where the user began rather than jumping in at their second contest.
   */
  get chartPoints(): ChartPoint[] {
    const history = this.rating?.history ?? [];
    if (history.length === 0) {
      return [];
    }

    const series = [
      { rating: history[0].before, label: 'Start' },
      ...history.map((h) => ({ rating: h.after, label: this.contestLabel(h.contestId) }))
    ];

    const values = series.map((s) => s.rating);
    const min = Math.min(...values);
    const max = Math.max(...values);
    // A flat series would divide by zero; give it a band so the line sits mid-height.
    const span = max === min ? 1 : max - min;
    const usableW = CHART_WIDTH - PAD_X * 2;
    const usableH = CHART_HEIGHT - PAD_Y * 2;
    const stepX = series.length === 1 ? 0 : usableW / (series.length - 1);

    return series.map((point, i) => ({
      x: PAD_X + stepX * i,
      y: PAD_Y + usableH - ((point.rating - min) / span) * usableH,
      rating: point.rating,
      label: point.label
    }));
  }

  get chartLine(): string {
    return this.chartPoints.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
  }

  get chartMax(): number {
    return Math.max(...this.chartPoints.map((p) => p.rating));
  }

  get chartMin(): number {
    return Math.min(...this.chartPoints.map((p) => p.rating));
  }

  get hasChart(): boolean {
    return this.chartPoints.length >= 2;
  }
}
