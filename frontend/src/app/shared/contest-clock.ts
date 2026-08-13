/**
 * Contest time, measured against the SERVER's clock.
 *
 * Every window decision in this system — the embargo, the standings freeze, the publish-on-end
 * sweep — is made server-side against `now()`. A page that reasoned from `Date.now()` would
 * disagree with all three on any machine whose clock is off, and a countdown is exactly where that
 * shows: it would reach zero while the backend still refuses the problems, or the reverse.
 *
 * So every contest payload carries `serverNow`, and these helpers work from a one-off skew measured
 * when the payload arrived. Skew is a constant, not a re-measured value: the two clocks tick at the
 * same rate, only their offset differs.
 */

export type ContestPhase = 'Upcoming' | 'Running' | 'Ended';

/** Milliseconds to add to the browser clock to land on server time. */
export function serverSkewMs(serverNow: string | undefined): number {
  if (!serverNow) {
    return 0;
  }
  const parsed = Date.parse(serverNow);
  return Number.isNaN(parsed) ? 0 : parsed - Date.now();
}

/** Server time now, in epoch ms. */
export function serverNowMs(skewMs: number): number {
  return Date.now() + skewMs;
}

export function phaseOf(startsAt: string, endsAt: string, nowMs: number): ContestPhase {
  if (nowMs < Date.parse(startsAt)) {
    return 'Upcoming';
  }
  if (nowMs > Date.parse(endsAt)) {
    return 'Ended';
  }
  return 'Running';
}

/** The instant a phase is counting down to, or null once the contest is over. */
export function phaseTargetMs(startsAt: string, endsAt: string, phase: ContestPhase): number | null {
  if (phase === 'Upcoming') {
    return Date.parse(startsAt);
  }
  if (phase === 'Running') {
    return Date.parse(endsAt);
  }
  return null;
}

/**
 * `2d 03:04:05`, `3:04:05`, or `04:05` — the largest unit present sets the width, so the string
 * never grows a digit mid-tick and shove the layout sideways. Negative input clamps to zero.
 */
export function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const pad = (n: number) => String(n).padStart(2, '0');

  if (days > 0) {
    return `${days}d ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  }
  if (hours > 0) {
    return `${hours}:${pad(minutes)}:${pad(seconds)}`;
  }
  return `${pad(minutes)}:${pad(seconds)}`;
}
