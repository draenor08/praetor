import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProblemDetail, ProblemSummary } from '../models/problem.model';
import { SubmissionCreated, SubmissionResponse, SubmitRequest } from '../models/submission.model';
import { ContestDetail, ContestSummary } from '../models/contest.model';
import { Standings } from '../models/standings.model';

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

  /** Re-run an existing submission (ADMIN only) — 202, re-judged async. */
  rejudge(id: number): Observable<SubmissionCreated> {
    return this.http.post<SubmissionCreated>(`/api/submissions/${id}/rejudge`, {});
  }

  getContests(): Observable<ContestSummary[]> {
    return this.http.get<ContestSummary[]>('/api/contests');
  }

  getContest(id: number): Observable<ContestDetail> {
    return this.http.get<ContestDetail>(`/api/contests/${id}`);
  }

  getStandings(id: number): Observable<Standings> {
    return this.http.get<Standings>(`/api/contests/${id}/standings`);
  }

  registerForContest(id: number): Observable<void> {
    return this.http.post<void>(`/api/contests/${id}/register`, { virtual: false });
  }
}
