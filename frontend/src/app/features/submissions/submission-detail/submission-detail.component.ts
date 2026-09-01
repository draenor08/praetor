import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../../../core/services/api.service';
import { WsService } from '../../../core/services/ws.service';
import { TokenService } from '../../../core/services/token.service';
import { SubmissionResponse } from '../../../core/models/submission.model';
import { ToastService } from '../../../shared/toast/toast.service';
import { markDirty } from '../../../core/rx/mark-dirty';

@Component({
  selector: 'app-submission-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './submission-detail.component.html',
  styleUrls: ['./submission-detail.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SubmissionDetailComponent implements OnInit, OnDestroy {
  private cdr = inject(ChangeDetectorRef);
  private api = inject(ApiService);
  private ws = inject(WsService);
  private tokenService = inject(TokenService);
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);

  private id!: number;
  private liveSub?: Subscription;

  submission?: SubmissionResponse;
  loading = true;
  error = '';
  rejudging = false;

  get isAdmin(): boolean {
    return this.tokenService.getUser()?.role === 'ADMIN';
  }

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  private load(): void {
    this.api.getSubmission(this.id).pipe(markDirty(this.cdr)).subscribe({
      next: (s) => {
        this.submission = s;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load this submission.';
        this.loading = false;
      }
    });
  }

  /** ADMIN re-run (FR-27). Reflects the re-judge live, then refreshes the full result on DONE. */
  rejudge(): void {
    if (this.rejudging) {
      return;
    }
    this.rejudging = true;
    this.liveSub?.unsubscribe();
    this.api.rejudge(this.id).pipe(markDirty(this.cdr)).subscribe({
      next: (created) => {
        if (this.submission) {
          this.submission = { ...this.submission, status: created.status, verdict: null, results: [] };
        }
        this.liveSub = this.ws.submission$(this.id).pipe(markDirty(this.cdr)).subscribe((ev) => {
          if (this.submission) {
            this.submission = { ...this.submission, status: ev.status, verdict: ev.verdict };
          }
          if (ev.status === 'DONE' || ev.status === 'ERROR') {
            this.rejudging = false;
            this.load(); // pull the fresh verdict + per-testcase table
          }
        });
      },
      error: () => {
        this.rejudging = false;
        this.toast.error('Rejudge failed.');
      }
    });
  }

  ngOnDestroy(): void {
    this.liveSub?.unsubscribe();
  }
  /** Keyed so a refresh reorders rows instead of rebuilding every one. */
  trackResult(index: number, r: any): any {
    return r.testCaseId ?? index;
  }

}
