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
