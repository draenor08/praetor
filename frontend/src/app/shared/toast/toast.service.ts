import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface ToastItem {
  id: number;
  message: string;
  type: ToastType;
  timeoutMs: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly toastsSubject = new BehaviorSubject<ToastItem[]>([]);
  readonly toasts$ = this.toastsSubject.asObservable();

  private nextId = 1;

  show(type: ToastType, message: string, timeoutMs = 4000): void {
    const toast: ToastItem = {
      id: this.nextId++,
      message,
      type,
      timeoutMs,
    };

    const current = this.toastsSubject.value;
    this.toastsSubject.next([...current, toast]);

    if (timeoutMs > 0) {
      setTimeout(() => this.dismiss(toast.id), timeoutMs);
    }
  }

  success(message: string, timeoutMs = 4000): void {
    this.show('success', message, timeoutMs);
  }

  error(message: string, timeoutMs = 5000): void {
    this.show('error', message, timeoutMs);
  }

  info(message: string, timeoutMs = 4000): void {
    this.show('info', message, timeoutMs);
  }

  warning(message: string, timeoutMs = 4500): void {
    this.show('warning', message, timeoutMs);
  }

  dismiss(id: number): void {
    const updated = this.toastsSubject.value.filter((toast) => toast.id !== id);
    this.toastsSubject.next(updated);
  }

  get toasts(): Observable<ToastItem[]> {
    return this.toasts$;
  }
}
