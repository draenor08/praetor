/** One participant's result on one contest problem. Mirrors the backend ProblemCell record. */
export interface ProblemCell {
  label: string;
  /** Rejected submissions before the AC (AC attempt not counted; CE never counts). */
  attempts: number;
  /** Whole minutes from contest start to the accepted submission; null if unsolved. */
  solvedAtMin: number | null;
  /** True = post-freeze activity hidden from this viewer (render pending "?"). */
  frozen: boolean;
}

/** One row of the ICPC board. */
export interface StandingsRow {
  rank: number;
  handle: string;
  solved: number;
  penalty: number;
  problems: ProblemCell[];
}

/**
 * GET /api/contests/{id}/standings snapshot AND each /topic|/user-queue standings push (same shape).
 * `frozen` = a freeze window is active right now (independent of viewer).
 */
export interface Standings {
  contestId: number;
  frozen: boolean;
  updatedAt: string;
  rows: StandingsRow[];
}
