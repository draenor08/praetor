import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

/** Problem authoring is staff work — the backend allows exactly these two roles. */
const STAFF_ROLES = ['PROBLEM_SETTER', 'ADMIN'];

/**
 * Every page is loaded on demand. The shell itself stays eager because it is the layout every
 * authenticated route renders inside, so deferring it would only delay the first paint.
 *
 * <p>Routes used to import their components at the top of this file, which put all 23 pages —
 * the setter workspace and the contest authoring screens included — into the bundle every visitor
 * downloads at the login screen. Guards stay eager: they are tiny, and they run before the chunk
 * they protect is fetched, so a contestant never downloads the staff pages at all.
 */
export const routes: Routes = [
  // Auth pages render outside the shell (no rail/topbar).
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent)
  },

  // Everything else lives inside the authenticated shell. Every section resolves to a real page —
  // ComingSoonComponent is no longer routed anywhere, and the component is kept only as the
  // placeholder to reach for if a future section needs one.
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'problems',
        loadComponent: () =>
          import('./features/problems/problem-list/problem-list.component').then((m) => m.ProblemListComponent)
      },
      {
        path: 'problems/:slug',
        loadComponent: () =>
          import('./features/problems/problem-detail/problem-detail.component').then((m) => m.ProblemDetailComponent)
      },
      {
        path: 'submissions/:id',
        loadComponent: () =>
          import('./features/submissions/submission-detail/submission-detail.component').then((m) => m.SubmissionDetailComponent)
      },
      {
        path: 'contests',
        loadComponent: () =>
          import('./features/contests/contest-list/contest-list.component').then((m) => m.ContestListComponent)
      },
      // Authoring routes must precede 'contests/:id', or 'new' is read as an id.
      {
        path: 'contests/new',
        loadComponent: () =>
          import('./features/contests/contest-create/contest-create.component').then((m) => m.ContestCreateComponent),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] }
      },
      {
        path: 'contests/:id/proposals',
        loadComponent: () =>
          import('./features/contests/contest-proposals/contest-proposals.component').then((m) => m.ContestProposalsComponent),
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      {
        path: 'contests/:id',
        loadComponent: () =>
          import('./features/contests/contest-detail/contest-detail.component').then((m) => m.ContestDetailComponent)
      },
      // Standings are per-contest, so the section lists contests and each opens its own board.
      // The contest page carries the same board inline; both share StandingsLiveComponent.
      {
        path: 'standings',
        loadComponent: () =>
          import('./features/contests/standings-list/standings-list.component').then((m) => m.StandingsListComponent)
      },
      {
        path: 'standings/:id',
        loadComponent: () =>
          import('./features/contests/contest-standings/contest-standings.component').then((m) => m.ContestStandingsComponent)
      },
      {
        path: 'submissions',
        loadComponent: () =>
          import('./features/submissions/submissions-list/submissions-list.component').then((m) => m.SubmissionsListComponent)
      },
      {
        path: 'leaderboard',
        loadComponent: () =>
          import('./features/leaderboard/leaderboard.component').then((m) => m.LeaderboardComponent)
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/profile/profile.component').then((m) => m.ProfileComponent)
      },

      // Setter workspace. roleGuard keeps contestants out of the UI; every endpoint behind it
      // re-checks the role server-side, so the guard is convenience, not the security boundary.
      {
        path: 'setter/calls',
        loadComponent: () =>
          import('./features/setter/contest-calls/contest-calls.component').then((m) => m.ContestCallsComponent),
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      {
        path: 'setter/problems',
        loadComponent: () =>
          import('./features/setter/problem-manage/problem-manage.component').then((m) => m.ProblemManageComponent),
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      {
        path: 'setter/problems/new',
        loadComponent: () =>
          import('./features/setter/problem-editor/problem-editor.component').then((m) => m.ProblemEditorComponent),
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      {
        path: 'setter/problems/:slug/edit',
        loadComponent: () =>
          import('./features/setter/problem-editor/problem-editor.component').then((m) => m.ProblemEditorComponent),
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      {
        path: 'setter/problems/:slug/testcases',
        loadComponent: () =>
          import('./features/setter/test-case-editor/test-case-editor.component').then((m) => m.TestCaseEditorComponent),
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },

      {
        path: 'about',
        loadComponent: () => import('./features/about/about.component').then((m) => m.AboutComponent)
      },
      // Home is the post-login index (it used to redirect to /problems). pathMatch 'full' keeps
      // an empty-path component route from matching every URL as a zero-segment prefix.
      {
        path: '',
        loadComponent: () => import('./features/landing/landing.component').then((m) => m.LandingComponent),
        pathMatch: 'full'
      },
      // Unknown URLs render a real 404 inside the shell (keeps rail + topbar).
      // Must stay LAST among the shell children so concrete paths match first.
      {
        path: '**',
        loadComponent: () =>
          import('./features/not-found/not-found.component').then((m) => m.NotFoundComponent)
      }
    ]
  }
];
