/**
 * Solve statistics for a profile (FR-25). Shape is fixed by docs/api-contracts.md.
 *
 * `accuracy` counts submissions rather than problems: five accepted attempts at one problem out
 * of ten submissions is 0.5, not 0.1. `byVerdict` only carries verdicts that actually occurred,
 * commonest first, and sums to `attempted`.
 */
export interface ProfileSolveStats {
  solved: number;
  attempted: number;
  accuracy: number; // 0.0 - 1.0
  byVerdict: Record<string, number>;
}
