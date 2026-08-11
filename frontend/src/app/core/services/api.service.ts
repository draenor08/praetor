import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BulkTestCaseInput,
  ManagedProblem,
  ProblemDetail,
  ProblemFull,
  ProblemInput,
  ProblemSummary,
  ProblemUsage,
  TestCaseRow
} from '../models/problem.model';
import { SubmissionCreated, SubmissionResponse, SubmitRequest } from '../models/submission.model';
import { ContestDetail, ContestSummary } from '../models/contest.model';
import { Standings } from '../models/standings.model';
import { Leaderboard, UserRating } from '../models/rating.model';

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

  // --- Ratings (public reads) ----------------------------------------------

  getLeaderboard(page = 0, size = 20): Observable<Leaderboard> {
    return this.http.get<Leaderboard>(`/api/leaderboard?page=${page}&size=${size}`);
  }

  getUserRating(handle: string): Observable<UserRating> {
    return this.http.get<UserRating>(`/api/users/${handle}/rating`);
  }

  // --- Setter workspace (PROBLEM_SETTER / ADMIN) ---------------------------
  // Reads live under /api/setter/** because GET /api/problems/* is anonymous, so a management
  // read parked there would arrive with no authenticated user at all. Writes reuse the
  // /api/problems paths (only GETs are public there).

  getManagedProblems(): Observable<ManagedProblem[]> {
    return this.http.get<ManagedProblem[]>('/api/setter/problems');
  }

  getManagedProblem(slug: string): Observable<ProblemFull> {
    return this.http.get<ProblemFull>(`/api/setter/problems/${slug}`);
  }

  getProblemUsage(slug: string): Observable<ProblemUsage> {
    return this.http.get<ProblemUsage>(`/api/setter/problems/${slug}/usage`);
  }

  createProblem(body: ProblemInput): Observable<ProblemFull> {
    return this.http.post<ProblemFull>('/api/problems', body);
  }

  updateProblem(slug: string, body: ProblemInput): Observable<ProblemFull> {
    return this.http.put<ProblemFull>(`/api/problems/${slug}`, body);
  }

  deleteProblem(slug: string): Observable<void> {
    return this.http.delete<void>(`/api/problems/${slug}`);
  }

  setProblemArchived(slug: string, archived: boolean): Observable<ProblemFull> {
    const action = archived ? 'archive' : 'unarchive';
    return this.http.post<ProblemFull>(`/api/problems/${slug}/${action}`, {});
  }

  getTestCases(slug: string): Observable<TestCaseRow[]> {
    return this.http.get<TestCaseRow[]>(`/api/problems/${slug}/testcases`);
  }

  saveTestCases(slug: string, body: BulkTestCaseInput): Observable<TestCaseRow[]> {
    return this.http.post<TestCaseRow[]>(`/api/problems/${slug}/testcases/bulk`, body);
  }
}
