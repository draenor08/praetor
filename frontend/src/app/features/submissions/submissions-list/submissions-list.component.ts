import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { TokenService } from '../../../core/services/token.service';
import { SubmissionSummary } from '../../../core/models/submission.model';
import { markDirty } from '../../../core/rx/mark-dirty';

const PAGE_SIZE = 20;

/**
 * Submission history (FR-10).
 *
 * <p>The list is scoped server-side: a contestant only ever receives their own rows, and asking
 * for another handle is a 403 rather than an empty page. Staff may filter to any handle, so the
 * filter box only appears for them — the guard here is convenience, the boundary is the API.
 *
 * <p>Rows carry no source code by design; the full submission, including per-test results, stays
 * behind the owner/ADMIN gate on its own page.
 */
@Component({
  selector: 'app-submissions-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './submissions-list.component.html',
  styleUrls: ['./submissions-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SubmissionsListComponent implements OnInit {
  private cdr = inject(ChangeDetectorRef);
  private api = inject(ApiService);
  private tokenService = inject(TokenService);

  rows: SubmissionSummary[] = [];
  page = 0;
  size = PAGE_SIZE;
  total = 0;
  loading = true;
  error = '';

  /** Staff-only handle filter. Empty means "my own history". */
  handleFilter = '';

  private appliedHandle = '';

  readonly me: string | null = this.tokenService.getUser()?.username ?? null;

  /** Same pair the backend calls staff, and the same pair that sees through a standings freeze. */
  get isStaff(): boolean {
    const role = this.tokenService.getUser()?.role;
    return role === 'ADMIN' || role === 'PROBLEM_SETTER';
  }

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.loading = true;
    this.error = '';
    this.api.getSubmissions({ user: this.appliedHandle || undefined, page, size: PAGE_SIZE })
      .pipe(markDirty(this.cdr)).subscribe({
        next: (result) => {
          this.rows = result.content;
          this.page = result.page;
          this.size = result.size;
          this.total = result.totalElements;
          this.loading = false;
        },
        error: (err) => {
          this.error = err?.status === 404
            ? `No user with the handle "${this.appliedHandle}".`
            : err?.error?.error ?? 'Could not load submissions.';
          this.rows = [];
          this.total = 0;
          this.loading = false;
        }
      });
  }

  /** Filtering restarts at page 0 — page 3 of one handle means nothing for another. */
  applyFilter(): void {
    this.appliedHandle = this.handleFilter.trim();
    this.load(0);
  }

  clearFilter(): void {
    this.handleFilter = '';
    this.appliedHandle = '';
    this.load(0);
  }

  get filtered(): boolean {
    return this.appliedHandle !== '';
  }

  get firstShown(): number {
    return this.total === 0 ? 0 : this.page * this.size + 1;
  }

  get lastShown(): number {
    return this.page * this.size + this.rows.length;
  }

  get hasPrev(): boolean {
    return this.page > 0;
  }

  get hasNext(): boolean {
    return this.lastShown < this.total;
  }
  /** Keyed so a refresh reorders rows instead of rebuilding every one. */
  trackRow(_index: number, s: any): any {
    return s.id;
  }

}
