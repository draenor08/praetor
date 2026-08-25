import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: string;
  type: ToastType;
  title?: string;
  message: string;
  duration: number;
}

export interface ToastOptions {
  title?: string;
  duration?: number;
}

/** Most toasts on screen at once; beyond this the oldest is dropped. */
const MAX_VISIBLE = 4;

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private readonly toastsSignal = signal<Toast[]>([]);
  readonly toasts = this.toastsSignal.asReadonly();

  private readonly timeouts = new Map<string, any>();

  /**
   * Display a generic toast message.
   */
  show(type: ToastType, message: string, options?: ToastOptions | string, duration?: number): string {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

    let title: string | undefined;
    let dur = 4000;

    if (typeof options === 'string') {
      title = options;
      dur = duration ?? dur;
    } else if (options) {
      title = options.title;
      dur = options.duration ?? duration ?? dur;
    } else {
      dur = duration ?? dur;
    }

    const toast: Toast = {
      id,
      type,
      title,
      message,
      duration: dur
    };

    // Cap the stack. The container is fixed-position with no scroll, so an unbounded burst — a
    // run of judged submissions arriving over the WebSocket, say — would push the oldest toasts
    // off-screen where nothing can dismiss them. Oldest out first.
    this.toastsSignal.update((list) => {
      const next = [...list, toast];
      const overflow = next.slice(0, Math.max(0, next.length - MAX_VISIBLE));
      overflow.forEach((t) => this.clearTimer(t.id));
      return next.slice(-MAX_VISIBLE);
    });

    if (dur > 0) {
      const handle = setTimeout(() => {
        this.dismiss(id);
      }, dur);
      this.timeouts.set(id, handle);
    }

    return id;
  }

  /** Display a success toast (default 4s). */
  success(message: string, options?: ToastOptions | string, duration?: number): string {
    return this.show('success', message, options, duration);
  }

  /** Display an error toast (default 5s). */
  error(message: string, options?: ToastOptions | string, duration?: number): string {
    return this.show('error', message, options, duration ?? 5000);
  }

  /** Display a warning toast (default 4.5s). */
  warning(message: string, options?: ToastOptions | string, duration?: number): string {
    return this.show('warning', message, options, duration ?? 4500);
  }

  /** Display an informational toast (default 4s). */
  info(message: string, options?: ToastOptions | string, duration?: number): string {
    return this.show('info', message, options, duration);
  }

  /** Dismiss a single toast by ID. */
  dismiss(id: string): void {
    this.clearTimer(id);
    this.toastsSignal.update((list) => list.filter((t) => t.id !== id));
  }

  /** Cancels a pending auto-dismiss without touching the list. */
  private clearTimer(id: string): void {
    const handle = this.timeouts.get(id);
    if (handle !== undefined) {
      clearTimeout(handle);
      this.timeouts.delete(id);
    }
  }

  /** Clear all active toasts immediately. */
  clear(): void {
    this.timeouts.forEach((handle) => clearTimeout(handle));
    this.timeouts.clear();
    this.toastsSignal.set([]);
  }
}
