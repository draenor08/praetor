/** GET /api/contests — one contest in the list (meta only). */
export interface ContestSummary {
  id: number;
  title: string;
  startsAt: string;
  endsAt: string;
  scoring: string;
}

/**
 * A problem slot in a contest. `label` and `ord` are always present — the standings board needs its
 * columns even for a spectator — but `slug` and `title` are null while the contest embargo applies,
 * so an unreachable statement is not linkable from the page's own payload.
 */
export interface ContestProblem {
  label: string;
  ord: number;
  problemId: number;
  slug: string | null;
  title: string | null;
}

/** GET /api/contests/{id} — contest meta + its problems in display order. */
export interface ContestDetail {
  id: number;
  title: string;
  startsAt: string;
  endsAt: string;
  freezeMin: number;
  scoring: string;
  /** Is the caller signed up for this contest (false when nobody is logged in). */
  registered: boolean;
  /** Do the slots carry slug + title, i.e. may the caller open the statements yet. */
  problemsVisible: boolean;
  problems: ContestProblem[];
}
