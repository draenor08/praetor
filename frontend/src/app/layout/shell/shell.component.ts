import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { TokenService } from '../../core/services/token.service';

interface RailLink {
  path: string;
  label: string;
  icon: string;
}

/**
 * Application shell: a fixed left rail (primary navigation) plus a slim topbar
 * (wordmark + current user + logout), with a child <router-outlet> for the
 * active feature page. Rendered as a layout route that wraps every
 * authenticated section; the auth pages (login/register) render outside it.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './shell.component.html',
  styleUrls: ['./shell.component.scss']
})
export class ShellComponent {
  private authService = inject(AuthService);
  private tokenService = inject(TokenService);
  private router = inject(Router);

  // Primary sections. The target pages are owned by different teammates and
  // land incrementally; until each ships, its route resolves to ComingSoon.
  readonly links: RailLink[] = [
    { path: '/problems', label: 'Problems', icon: '§' },
    { path: '/contests', label: 'Contests', icon: '☰' },
    { path: '/standings', label: 'Standings', icon: '▤' },
    { path: '/submissions', label: 'Submissions', icon: '⟳' },
    { path: '/profile', label: 'Profile', icon: '◈' },
    { path: '/about', label: 'About', icon: 'ⓘ' }
  ];

  get username(): string {
    return this.tokenService.getUser()?.username ?? 'user';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
