import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit {

  private authService = inject(AuthService);

  user: any = null;
  loading = true;
  error = '';

  ngOnInit(): void {
    this.authService.getMe().subscribe({
      next: (user) => {
        this.user = user;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load profile.';
        this.loading = false;
      }
    });
  }
}