import { ChangeDetectorRef } from '@angular/core';
import { MonoTypeOperatorFunction, tap } from 'rxjs';

/**
 * Marks an OnPush component dirty whenever a stream it renders from emits, errors or completes.
 *
 * <p>An OnPush component is only re-checked when one of its inputs changes by reference, an event
 * fires inside it, a signal it reads changes, or an async pipe emits. Assigning a field from inside
 * a `subscribe` callback is none of those, so the screen would silently stop updating — the kind of
 * failure that looks like a hang and shows up in a demo rather than in a build.
 *
 * <p>Written as one operator rather than a `markForCheck()` at the end of every handler because a
 * handler that is forgotten produces exactly that silent failure, and a missing `.pipe(markDirty())`
 * can be grepped for. Marking happens before the subscriber assigns, which is fine: `markForCheck`
 * only sets a flag, and the check itself runs once the whole callback chain has finished.
 */
export function markDirty<T>(cdr: ChangeDetectorRef): MonoTypeOperatorFunction<T> {
  return (source) =>
    source.pipe(
      tap({
        next: () => cdr.markForCheck(),
        error: () => cdr.markForCheck(),
        complete: () => cdr.markForCheck()
      })
    );
}
