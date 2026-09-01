import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { ManagedProblem } from '../../../core/models/problem.model';
import { markDirty } from '../../../core/rx/mark-dirty';

/**
 * Setter workspace: every problem with the actions its current state allows.
 *
 * <p>The row carries `deletable` / `lockReason` from the backend, so Delete is only ever offered
 * when the DELETE endpoint would actually accept it — otherwise the row offers Archive and says
 * why. That keeps a setter from clicking into a 409.
 */
@Component({
  selector: 'app-problem-manage',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './problem-manage.component.html',
  styleUrls: ['./problem-manage.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProblemManageComponent implements OnInit {
  private cdr = inject(ChangeDetectorRef);
  private api = inject(ApiService);

  problems: ManagedProblem[] = [];
  loading = true;
  error = '';
  notice = '';

  /** Slug whose delete is awaiting confirmation — deleting is irreversible. */
  pendingDelete: string | null = null;
  /** Slug awaiting confirmation that publishing it (Restore) is intended. */
  pendingRestore: string | null = null;
  busySlug: string | null = null;

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading = true;
    this.api.getManagedProblems().pipe(markDirty(this.cdr)).subscribe({
      next: (list) => {
        this.problems = list;
        this.loading = false;
      },
      error: (err) => {
        this.error = this.messageFrom(err, 'Could not load problems.');
        this.loading = false;
      }
    });
  }

  askDelete(slug: string): void {
    this.pendingDelete = slug;
    this.notice = '';
    this.error = '';
  }

  cancelDelete(): void {
    this.pendingDelete = null;
  }

  confirmDelete(slug: string): void {
    this.busySlug = slug;
    this.api.deleteProblem(slug).pipe(markDirty(this.cdr)).subscribe({
      next: () => {
        this.pendingDelete = null;
        this.busySlug = null;
        this.notice = `Deleted ${slug}.`;
        this.load();
      },
      error: (err) => {
        this.busySlug = null;
        this.pendingDelete = null;
        // Reachable if the problem gained a submission between the list load and this click —
        // the guard is the authority, so surface its reason and refresh.
        this.error = this.messageFrom(err, 'Could not delete this problem.');
        this.load();
      }
    });
  }

  toggleArchived(problem: ManagedProblem): void {
    // Restoring publishes the statement, and publication is one-way: the problem can never go into
    // a contest afterwards. Worth a confirm, since the button itself looks reversible.
    if (problem.archived && this.pendingRestore !== problem.slug) {
      this.pendingRestore = problem.slug;
      return;
    }
    this.pendingRestore = null;
    this.busySlug = problem.slug;
    this.error = '';
    this.api.setProblemArchived(problem.slug, !problem.archived).pipe(markDirty(this.cdr)).subscribe({
      next: () => {
        this.busySlug = null;
        this.notice = problem.archived
          ? `${problem.slug} is public again.`
          : `${problem.slug} is archived — hidden from the problem list, submissions kept.`;
        this.load();
      },
      error: (err) => {
        this.busySlug = null;
        this.error = this.messageFrom(err, 'Could not change archive state.');
      }
    });
  }

  private messageFrom(err: any, fallback: string): string {
    return err?.error?.error ?? err?.error?.message ?? fallback;
  }
  /** Keyed so a refresh reorders rows instead of rebuilding every one. */
  trackManaged(_index: number, p: any): any {
    return p.slug;
  }

}
