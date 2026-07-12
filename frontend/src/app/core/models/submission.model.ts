/** POST /api/submissions request body. contestId omitted = practice submission. */
export interface SubmitRequest {
  problemSlug: string;
  contestId?: number;
  language: string;
  sourceCode: string;
}

/** 202 Accepted body for POST /api/submissions. */
export interface SubmissionCreated {
  id: number;
  status: string;
}

/** One test case's outcome. */
export interface TestResult {
  ord: number;
  verdict: string;
  timeMs: number | null;
  memKb: number | null;
}

/** GET /api/submissions/{id}. verdict/timeMs/memKb null and results empty until judged. */
export interface SubmissionResponse {
  id: number;
  handle: string;
  problemSlug: string;
  language: string;
  status: string;
  verdict: string | null;
  timeMs: number | null;
  memKb: number | null;
  compileLog: string | null;
  createdAt: string;
  results: TestResult[];
}
