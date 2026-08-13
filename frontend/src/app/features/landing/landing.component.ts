import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ApiService } from '../../core/services/api.service';
import { UserRating } from '../../core/models/rating.model';

/** One row of the activity feed. Shaped now so FR-10 only has to fill the list. */
interface ActivityRow {
  at: string;
  problem: string;
  verdict: string;
}

/** The three counters the stat cards want. FR-25 is the endpoint that would serve them. */
interface SolveStats {
  solved: number;
  submissions: number;
  accuracyPct: number;
}

/**
 * Post-login home. Rating, global rank and the last contest's delta are real — they come from
 * GET /api/users/{handle}/rating, the same endpoint the profile page reads.
 *
 * Solve stats (FR-25) and submission history (FR-10) have no endpoint at any layer yet, and they
 * are a teammate's features. Rather than ship the invented numbers this page used to hardcode
 * (73 solved / 104 submissions / rank #12), those cards render as visibly-marked placeholders.
 * Each is one flag plus one empty loader: when the endpoint lands, flip the flag and fill the
 * method — no template surgery.
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

  /** FR-25 solve stats — unbuilt. Flip to true once loadSolveStats() has something to call. */
  readonly solveStatsTracked = false;
  solveStats: SolveStats | null = null;

  /** FR-10 submission history — unbuilt. Same contract as above. */
  readonly activityTracked = false;
  activity: ActivityRow[] = [];

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
      },
      error: () => {
        this.error = 'Could not load your dashboard.';
        this.loading = false;
      }
    });

    this.loadSolveStats();
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
   * FR-25 (solve stats) — Mahir's feature, unbuilt at every layer. When the endpoint exists:
   * call it here and assign this.solveStats, then set solveStatsTracked to true.
   */
  private loadSolveStats(): void {
    this.solveStats = null;
  }

  /**
   * FR-10 (submission history) — same: fill this.activity from the user's recent submissions
   * and set activityTracked to true.
   */
  private loadRecentActivity(): void {
    this.activity = [];
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
