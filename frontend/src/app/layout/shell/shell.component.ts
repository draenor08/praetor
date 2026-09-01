import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { TokenService } from '../../core/services/token.service';

interface RailLink {
  path: string;
  label: string;
  icon: string;
  /** When set, the link only renders for these roles. */
  roles?: string[];
  /**
   * Exact-match the active highlight. Only Home needs it: its path is '/', which is a prefix of
   * every route, so prefix matching would leave Home lit on every page.
   */
  exact?: boolean;
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
  styleUrls: ['./shell.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShellComponent {
  private authService = inject(AuthService);
  private tokenService = inject(TokenService);
  private router = inject(Router);

  // Primary sections. The target pages are owned by different teammates and
  // landed incrementally; as of FR-10 every one of them resolves to a real page.
  readonly links: RailLink[] = [
    { path: '/', label: 'Home', icon: '⌂', exact: true },
    { path: '/problems', label: 'Problems', icon: '§' },
    { path: '/contests', label: 'Contests', icon: '☰' },
    { path: '/standings', label: 'Standings', icon: '▤' },
    { path: '/submissions', label: 'Submissions', icon: '⟳' },
    { path: '/leaderboard', label: 'Leaderboard', icon: '★' },
    { path: '/profile', label: 'Profile', icon: '◈' },
    { path: '/setter/problems', label: 'Manage', icon: '✎', roles: ['PROBLEM_SETTER', 'ADMIN'] },
    { path: '/setter/calls', label: 'Calls', icon: '✉', roles: ['PROBLEM_SETTER', 'ADMIN'] },
    { path: '/about', label: 'About', icon: 'ⓘ' }
  ];

  /** Hides staff-only sections from contestants; roleGuard and the API still enforce access. */
  get visibleLinks(): RailLink[] {
    const role = this.tokenService.getUser()?.role;
    return this.links.filter((link) => !link.roles || link.roles.includes(role));
  }

  get username(): string {
    return this.tokenService.getUser()?.username ?? 'user';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
