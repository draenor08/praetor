/**
 * POST /api/submissions request body.
 *
 * There is no contestId: the server decides whether a submission scores in a contest, from the
 * caller's registrations and the contest window. This interface used to carry an optional one, and
 * because nothing in the app ever set it, every submission made in a live round was recorded as
 * practice.
 */
export interface SubmitRequest {
  problemSlug: string;
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

/**
 * One row of GET /api/submissions (FR-10). Deliberately lighter than SubmissionResponse: no
 * sourceCode and no per-test results, so the list cannot route around the owner/ADMIN gate on
 * GET /api/submissions/{id}.
 */
export interface SubmissionSummary {
  id: number;
  handle: string;
  problemSlug: string;
  language: string;
  status: string;
  verdict: string | null;
  score: number | null;
  createdAt: string;
}

/** GET /api/submissions — paginated history, newest first. */
export interface SubmissionPage {
  content: SubmissionSummary[];
  page: number;
  size: number;
  totalElements: number;
}
