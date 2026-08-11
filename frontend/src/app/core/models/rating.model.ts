/** One row of GET /api/leaderboard. Equal ratings share a rank, then the next rank skips. */
export interface LeaderboardEntry {
  rank: number;
  handle: string;
  rating: number;
}

/** GET /api/leaderboard?page&size — public, no token needed. */
export interface Leaderboard {
  content: LeaderboardEntry[];
  page: number;
  size: number;
  totalElements: number;
}

/** One rating change, written when a contest's results are applied. */
export interface RatingHistoryEntry {
  contestId: number;
  before: number;
  after: number;
  at: string;
}

/** GET /api/users/{handle}/rating — rating, live rank, and the full change history. */
export interface UserRating {
  rating: number;
  rank: number;
  history: RatingHistoryEntry[];
}
