import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProblemDetail, ProblemSummary } from '../models/problem.model';
import { SubmissionCreated, SubmissionResponse, SubmitRequest } from '../models/submission.model';

/**
 * REST client for problems and submissions. Paths are relative ('/api/...'), proxied to the backend
 * by nginx; the JWT is attached by jwtInterceptor. Mirrors the AuthService HTTP pattern.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  getProblems(): Observable<ProblemSummary[]> {
    return this.http.get<ProblemSummary[]>('/api/problems');
  }

  getProblem(slug: string): Observable<ProblemDetail> {
    return this.http.get<ProblemDetail>(`/api/problems/${slug}`);
  }

  submit(req: SubmitRequest): Observable<SubmissionCreated> {
    return this.http.post<SubmissionCreated>('/api/submissions', req);
  }

  getSubmission(id: number): Observable<SubmissionResponse> {
    return this.http.get<SubmissionResponse>(`/api/submissions/${id}`);
  }
}
