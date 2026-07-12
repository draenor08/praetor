import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { ProblemSummary } from '../../../core/models/problem.model';

@Component({
  selector: 'app-problem-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './problem-list.component.html',
  styleUrls: ['./problem-list.component.scss']
})
export class ProblemListComponent implements OnInit {
  private api = inject(ApiService);

  problems: ProblemSummary[] = [];
  loading = true;
  error = '';

  ngOnInit(): void {
    this.api.getProblems().subscribe({
      next: (list) => {
        this.problems = list;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load problems.';
        this.loading = false;
      }
    });
  }
}
