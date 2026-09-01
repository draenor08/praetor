import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { TokenService } from '../../core/services/token.service';
import { LeaderboardEntry } from '../../core/models/rating.model';
import { markDirty } from '../../core/rx/mark-dirty';

const PAGE_SIZE = 20;

/**
 * Rating leaderboard.
 *
 * <p>Ranks come from the server (a window function over the whole table), so they stay global
 * across pages and tied ratings share a rank — the page never renumbers rows itself.
 */
@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './leaderboard.component.html',
  styleUrls: ['./leaderboard.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LeaderboardComponent implements OnInit {
  private cdr = inject(ChangeDetectorRef);
  private api = inject(ApiService);
  private tokenService = inject(TokenService);

  entries: LeaderboardEntry[] = [];
  page = 0;
  size = PAGE_SIZE;
  total = 0;
  loading = true;
  error = '';

  /** Highlights the viewer's own row. */
  readonly me: string | null = this.tokenService.getUser()?.username ?? null;

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.loading = true;
    this.error = '';
    this.api.getLeaderboard(page, PAGE_SIZE).pipe(markDirty(this.cdr)).subscribe({
      next: (board) => {
        this.entries = board.content;
        this.page = board.page;
        this.size = board.size;
        this.total = board.totalElements;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.error ?? 'Could not load the leaderboard.';
        this.loading = false;
      }
    });
  }

  get firstShown(): number {
    return this.total === 0 ? 0 : this.page * this.size + 1;
  }

  get lastShown(): number {
    return this.page * this.size + this.entries.length;
  }

  get hasPrev(): boolean {
    return this.page > 0;
  }

  get hasNext(): boolean {
    return this.lastShown < this.total;
  }
  /** Keyed so a refresh reorders rows instead of rebuilding every one. */
  trackEntry(_index: number, e: any): any {
    return e.handle;
  }

}
