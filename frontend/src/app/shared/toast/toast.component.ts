import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ToastItem, ToastService } from './toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './toast.component.html',
  styleUrls: ['./toast.component.scss']
})
export class ToastComponent {
  private readonly toastService = inject(ToastService);

  readonly toasts = this.toastService.toasts;

  dismiss(id: number): void {
    this.toastService.dismiss(id);
  }

  iconFor(toast: ToastItem): string {
    switch (toast.type) {
      case 'success':
        return '✓';
      case 'error':
        return '✕';
      case 'warning':
        return '!';
      default:
        return 'i';
    }
  }
}
