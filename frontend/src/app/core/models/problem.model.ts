/** GET /api/problems row. */
export interface ProblemSummary {
  slug: string;
  title: string;
  difficulty: number;
  judgeMode: string;
}

/** One visible sample test case. */
export interface Sample {
  ord: number;
  input: string;
  expected: string;
}

/** GET /api/problems/{slug}. */
export interface ProblemDetail {
  slug: string;
  title: string;
  statement: string;
  constraints: string | null;
  difficulty: number;
  timeLimitMs: number;
  memLimitKb: number;
  judgeMode: string;
  samples: Sample[];
}

// ---------------------------------------------------------------------------
// Setter workspace (PROBLEM_SETTER / ADMIN). Public pages use the types above.
// ---------------------------------------------------------------------------

/**
 * GET /api/setter/problems row. `deletable` and `lockReason` are computed by the backend from
 * the same rule the DELETE guard uses — the UI shows them, it does not re-derive them.
 */
export interface ManagedProblem {
  slug: string;
  title: string;
  difficulty: number;
  judgeMode: string;
  archived: boolean;
  testCases: number;
  submissions: number;
  contests: number;
  inLiveContest: boolean;
  deletable: boolean;
  lockReason: string | null;
}

/** GET /api/setter/problems/{slug} — every editable field. */
export interface ProblemFull {
  id: number;
  slug: string;
  title: string;
  statement: string;
  constraints: string | null;
  difficulty: number;
  timeLimitMs: number;
  memLimitKb: number;
  judgeMode: string;
  floatEps: number | null;
  checkerCode: string | null;
  editorial: string | null;
  createdBy: number | null;
  archived: boolean;
}

/** POST/PUT /api/problems body. */
export interface ProblemInput {
  slug: string;
  title: string;
  statement: string;
  constraints: string | null;
  difficulty: number;
  timeLimitMs: number;
  memLimitKb: number;
  judgeMode: string;
  floatEps: number | null;
  checkerCode: string | null;
  editorial: string | null;
}

/** GET /api/setter/problems/{slug}/usage — why delete is or isn't allowed. */
export interface ProblemUsage {
  slug: string;
  deletable: boolean;
  archived: boolean;
  inLiveContest: boolean;
  submissions: number;
  clarifications: number;
  contestTitle: string | null;
  reason: string | null;
}

/** A test case as the setter edits it. `id` is absent for rows not yet saved. */
export interface TestCaseRow {
  id?: number;
  ord: number;
  kind: 'SAMPLE' | 'HIDDEN';
  input: string;
  expected: string;
  points: number;
}

/** POST /api/problems/{slug}/testcases/bulk body. REPLACE wipes every existing case first. */
export interface BulkTestCaseInput {
  mode: 'APPEND' | 'REPLACE';
  cases: TestCaseRow[];
}
