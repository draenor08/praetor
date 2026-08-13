/** GET /api/problems row. `tags` is always present, empty when the problem has none. */
export interface ProblemSummary {
  slug: string;
  title: string;
  difficulty: number;
  judgeMode: string;
  tags: string[];
}

/**
 * Query for GET /api/problems (FR-15). Every field is optional and an omitted one is inert, so an
 * empty filter is the plain full list. `tags` is AND — a problem must carry all of them.
 */
export interface ProblemFilter {
  q?: string;
  minDifficulty?: number | null;
  maxDifficulty?: number | null;
  tags?: string[];
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
  tags: string[];
  /**
   * The setter's solution write-up (FR-16), or null when the caller has not earned it: staff always,
   * everyone else only after solving it and only while no contest is using the problem. Absence is
   * the enforcement — the server omits it rather than trusting the client to hide it.
   */
  editorial: string | null;
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
  tags: string[];
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
  /** Replaces the problem's tags outright. Omit to leave them untouched; empty clears them. */
  tags?: string[] | null;
  /**
   * Create it as a draft: archived and unpublished, so a contest can still use it. Only read on
   * create — publication is one-way, so an existing problem's status is never changed this way.
   */
  draft?: boolean | null;
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
