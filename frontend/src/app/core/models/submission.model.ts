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

/** One test case's outcome. input/expected/actualOutput are the practice reveal (feat 3d) —
 * present only on the first failing row of a practice submission; null everywhere else. */
export interface TestResult {
  ord: number;
  verdict: string;
  timeMs: number | null;
  memKb: number | null;
  input?: string | null;
  expected?: string | null;
  actualOutput?: string | null;
}

/** GET /api/submissions/{id}. verdict/timeMs/memKb null and results empty until judged.
 * practice = not part of a contest (contestId == null) → the failing-case reveal may be shown. */
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
  practice: boolean;
  results: TestResult[];
}
