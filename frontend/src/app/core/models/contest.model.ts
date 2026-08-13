/** GET /api/contests — one contest in the list (meta only). */
export interface ContestSummary {
  id: number;
  title: string;
  startsAt: string;
  endsAt: string;
  scoring: string;
  /** Setters may currently propose problems for this contest. */
  callsOpen: boolean;
  /**
   * The server instant this response was built at. Time-relative UI measures against this, not the
   * browser clock — the backend's window rules are what it is describing. See shared/contest-clock.
   */
  serverNow: string;
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
  /** Setters may currently propose problems for this contest. */
  callsOpen: boolean;
  /** Do the slots carry slug + title, i.e. may the caller open the statements yet. */
  problemsVisible: boolean;
  problems: ContestProblem[];
  /** Server instant of this response — same contract as on {@link ContestSummary}. */
  serverNow: string;
}

/** A draft problem a contest may still use. */
export interface EligibleProblem {
  problemId: number;
  slug: string;
  title: string;
  difficulty: number;
  judgeMode: string;
  author: string;
  testCases: number;
}

/** A setter's offer of a problem for a contest. */
export interface Proposal {
  id: number;
  contestId: number | null;
  problemId: number;
  slug: string;
  title: string;
  difficulty: number;
  judgeMode: string;
  proposedBy: string;
  status: 'PROPOSED' | 'ACCEPTED' | 'REJECTED';
  note: string | null;
  testCases: number;
  createdAt: string;
}

/** One slot in a create-contest request. */
export interface ContestProblemSpec {
  problemId: number;
  label: string;
  ord: number;
}

/** POST /api/contests body. `problems` may be empty when `callsOpen` is true. */
export interface CreateContestRequest {
  title: string;
  startsAt: string;
  endsAt: string;
  freezeMin: number;
  scoring: string;
  problems: ContestProblemSpec[];
  callsOpen: boolean;
}
