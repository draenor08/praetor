import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ContestPhase,
  formatRemaining,
  phaseOf,
  phaseTargetMs,
  serverNowMs,
  serverSkewMs
} from '../../contest-clock';

/**
 * Live countdown for one contest: "Starts in 1:12:07", "Ends in 44:02", or "Ended".
 *
 * Runs off the server clock (see contest-clock.ts) rather than the browser's, because it is
 * describing the same window the backend enforces.
 *
 * When the phase changes under it — the contest starts, or ends — it emits {@link phaseChange}.
 * Whether the problems may now be opened is the backend's call, so the parent's job on that event is
 * to re-fetch, not to flip a flag locally.
 */
@Component({
  selector: 'app-countdown',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="countdown" [class.countdown-soon]="soon" *ngIf="startsAt && endsAt">
      <ng-container *ngIf="phase === 'Upcoming'">Starts in <b>{{ remaining }}</b></ng-container>
      <ng-container *ngIf="phase === 'Running'">Ends in <b>{{ remaining }}</b></ng-container>
      <ng-container *ngIf="phase === 'Ended'">Ended</ng-container>
    </span>
  `,
  styles: [`
    .countdown {
      color: var(--text-muted);
      font-size: 13px;
      font-variant-numeric: tabular-nums;
    }

    .countdown b {
      color: var(--text-color);
      font-weight: 600;
    }

    /* The last five minutes of either phase are the ones people watch. */
    .countdown-soon b {
      color: var(--primary-color);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CountdownComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) startsAt!: string;
  @Input({ required: true }) endsAt!: string;

  /** The server instant carried by the payload these times came from. */
  @Input() serverNow?: string;

  @Output() phaseChange = new EventEmitter<ContestPhase>();

  phase: ContestPhase = 'Upcoming';
  remaining = '';
  soon = false;

  private cdr = inject(ChangeDetectorRef);
  private skewMs = 0;
  private timer?: ReturnType<typeof setInterval>;

  /** Re-measure the skew whenever a fresh payload arrives, then tick from it. */
  ngOnChanges(): void {
    this.skewMs = serverSkewMs(this.serverNow);
    this.phase = phaseOf(this.startsAt, this.endsAt, serverNowMs(this.skewMs));
    this.tick();
    if (!this.timer) {
      // The tick mutates state from a timer, which is not an input, an event or a signal — under
      // OnPush the clock would render once and then sit still.
      this.timer = setInterval(() => {
        this.tick();
        this.cdr.markForCheck();
      }, 1000);
    }
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  private tick(): void {
    if (!this.startsAt || !this.endsAt) {
      return;
    }
    const now = serverNowMs(this.skewMs);
    const phase = phaseOf(this.startsAt, this.endsAt, now);
    if (phase !== this.phase) {
      this.phase = phase;
      this.phaseChange.emit(phase);
    }

    const target = phaseTargetMs(this.startsAt, this.endsAt, phase);
    if (target == null) {
      this.remaining = '';
      this.soon = false;
      // Nothing left to count. Stop burning a timer on an ended contest.
      if (this.timer) {
        clearInterval(this.timer);
        this.timer = undefined;
      }
      return;
    }
    const left = target - now;
    this.remaining = formatRemaining(left);
    this.soon = left <= 5 * 60 * 1000;
  }
}
