/** GET /api/contests — one contest in the list (meta only). */
export interface ContestSummary {
  id: number;
  title: string;
  startsAt: string;
  endsAt: string;
  scoring: string;
}

/** A problem slot in a contest (display label + its order). */
export interface ContestProblem {
  label: string;
  ord: number;
  problemId: number;
}

/** GET /api/contests/{id} — contest meta + its problems in display order. */
export interface ContestDetail {
  id: number;
  title: string;
  startsAt: string;
  endsAt: string;
  freezeMin: number;
  scoring: string;
  problems: ContestProblem[];
}
