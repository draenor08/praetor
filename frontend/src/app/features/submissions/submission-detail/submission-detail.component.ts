import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { SubmissionResponse } from '../../../core/models/submission.model';

@Component({
  selector: 'app-submission-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './submission-detail.component.html',
  styleUrls: ['./submission-detail.component.scss']
})
export class SubmissionDetailComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);

  submission?: SubmissionResponse;
  loading = true;
  error = '';

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getSubmission(id).subscribe({
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
}
