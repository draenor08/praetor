import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ApiService } from '../../core/services/api.service';
import { UserRating } from '../../core/models/rating.model';
import { ProfileSolveStats } from '../../core/models/profile.model';
import { SubmissionSummary } from '../../core/models/submission.model';

/** How many recent submissions the activity feed shows before deferring to /submissions. */
const RECENT_COUNT = 5;

/**
 * Post-login home. Rating, global rank and the last contest's delta are real — they come from
 * GET /api/users/{handle}/rating, the same endpoint the profile page reads.
 *
 * Solve stats (FR-25) and submission history (FR-10) are real now too. They were placeholders
 * while those features were unbuilt — deliberately marked as such rather than shipping the
 * invented numbers this page once hardcoded (73 solved / 104 submissions / rank #12). Both are
 * wired to their endpoints as of #41/#44/#45.
 *
 * <p>Every card degrades to an em-dash rather than a zero when its call fails: a dashboard that
 * silently reports 0 solved to someone who has solved plenty is worse than one that admits it
 * does not know.
 */
@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.scss']
})
export class LandingComponent implements OnInit {
  private authService = inject(AuthService);
  private api = inject(ApiService);

  user: any = null;
  rating?: UserRating;
  loading = true;
  error = '';

  solveStats: ProfileSolveStats | null = null;
  activity: SubmissionSummary[] = [];

  /** Distinguishes "loaded, and there is nothing" from "could not load" — different messages. */
  private activityLoaded = false;

  get solveStatsTracked(): boolean {
    return this.solveStats !== null;
  }

  get activityTracked(): boolean {
    return this.activityLoaded;
  }

  /** Whole percent is enough on a dashboard tile; the profile page carries the precise figure. */
  get accuracyPct(): number {
    return Math.round((this.solveStats?.accuracy ?? 0) * 100);
  }

  features = [
    {
      title: 'Adaptive practice',
      description: 'Work through curated challenges that match your current skill level and growth goals.'
    },
    {
      title: 'Live contest flow',
      description: 'Join timed rounds, follow your standing, and review feedback right after each run.'
    },
    {
      title: 'Clear progress insights',
      description: 'Track your streaks, submissions, and problem history in a focused performance view.'
    }
  ];

  ngOnInit(): void {
    this.authService.getMe().subscribe({
      next: (user) => {
        this.user = user;
        this.loading = false;
        this.loadRating(user?.username);
        // Solve stats are keyed by handle, so this one has to wait for /users/me to answer.
        this.loadSolveStats(user?.username);
      },
      error: () => {
        this.error = 'Could not load your dashboard.';
        this.loading = false;
      }
    });

    // The history endpoint defaults to the caller's own rows, so it needs no handle.
    this.loadRecentActivity();
  }

  private loadRating(handle?: string): void {
    if (!handle) {
      return;
    }
    // Rating lives on its own endpoint because it carries the live rank and the change history;
    // /api/users/me only knows the current value. A failure leaves the card reading "—".
    this.api.getUserRating(handle).subscribe({
      next: (rating) => (this.rating = rating),
      error: () => undefined
    });
  }

  /**
   * Solve statistics (FR-25). These counts exclude anything from a contest that has not ended, so
   * a figure here does not move mid-round — that is the standings freeze holding, not a bug.
   */
  private loadSolveStats(handle?: string): void {
    if (!handle) {
      return;
    }
    this.api.getUserSolveStats(handle).subscribe({
      next: (stats) => (this.solveStats = stats),
      error: () => (this.solveStats = null)
    });
  }

  /** The last few submissions (FR-10). The full list lives at /submissions. */
  private loadRecentActivity(): void {
    this.api.getSubmissions({ page: 0, size: RECENT_COUNT }).subscribe({
      next: (page) => {
        this.activity = page.content;
        this.activityLoaded = true;
      },
      error: () => {
        this.activity = [];
        this.activityLoaded = false;
      }
    });
  }

  get initial(): string {
    return (this.user?.username ?? '?').charAt(0).toUpperCase();
  }

  /** Current rating: the rating endpoint first, the cached user as a fallback. */
  get ratingValue(): number | null {
    return this.rating?.rating ?? this.user?.rating ?? null;
  }

  /** The most recent rating change, or null before the user's first rated contest. */
  get lastDelta(): number | null {
    const history = this.rating?.history ?? [];
    if (history.length === 0) {
      return null;
    }
    const latest = history[history.length - 1];
    return latest.after - latest.before;
  }
}
