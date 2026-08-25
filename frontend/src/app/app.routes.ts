import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { RegisterComponent } from './features/auth/register/register.component';
import { ShellComponent } from './layout/shell/shell.component';
import { ProblemListComponent } from './features/problems/problem-list/problem-list.component';
import { ProblemDetailComponent } from './features/problems/problem-detail/problem-detail.component';
import { SubmissionDetailComponent } from './features/submissions/submission-detail/submission-detail.component';
import { SubmissionsListComponent } from './features/submissions/submissions-list/submissions-list.component';
import { ContestListComponent } from './features/contests/contest-list/contest-list.component';
import { ContestDetailComponent } from './features/contests/contest-detail/contest-detail.component';
import { StandingsListComponent } from './features/contests/standings-list/standings-list.component';
import { ContestCreateComponent } from './features/contests/contest-create/contest-create.component';
import { ContestProposalsComponent } from './features/contests/contest-proposals/contest-proposals.component';
import { ContestCallsComponent } from './features/setter/contest-calls/contest-calls.component';
import { ContestStandingsComponent } from './features/contests/contest-standings/contest-standings.component';
import { AboutComponent } from './features/about/about.component';
import { LandingComponent } from './features/landing/landing.component';
import { NotFoundComponent } from './features/not-found/not-found.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { ProfileComponent } from './features/profile/profile.component';
import { LeaderboardComponent } from './features/leaderboard/leaderboard.component';
import { ProblemManageComponent } from './features/setter/problem-manage/problem-manage.component';
import { ProblemEditorComponent } from './features/setter/problem-editor/problem-editor.component';
import { TestCaseEditorComponent } from './features/setter/test-case-editor/test-case-editor.component';

/** Problem authoring is staff work — the backend allows exactly these two roles. */
const STAFF_ROLES = ['PROBLEM_SETTER', 'ADMIN'];

export const routes: Routes = [
  // Auth pages render outside the shell (no rail/topbar).
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },

  // Everything else lives inside the authenticated shell. Every section now resolves to a real
  // page — ComingSoonComponent is no longer routed anywhere, and the component is kept only as
  // the placeholder to reach for if a future section needs one.
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: 'problems', component: ProblemListComponent },
      { path: 'problems/:slug', component: ProblemDetailComponent },
      { path: 'submissions/:id', component: SubmissionDetailComponent },
      { path: 'contests', component: ContestListComponent },
      // Authoring routes must precede 'contests/:id', or 'new' is read as an id.
      {
        path: 'contests/new',
        component: ContestCreateComponent,
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] }
      },
      {
        path: 'contests/:id/proposals',
        component: ContestProposalsComponent,
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      { path: 'contests/:id', component: ContestDetailComponent },
      // Standings are per-contest, so the section lists contests and each opens its own board.
      // The contest page carries the same board inline; both share StandingsLiveComponent.
      { path: 'standings', component: StandingsListComponent },
      { path: 'standings/:id', component: ContestStandingsComponent },
      { path: 'submissions', component: SubmissionsListComponent },
      { path: 'leaderboard', component: LeaderboardComponent },
      { path: 'profile', component: ProfileComponent },

      // Setter workspace. roleGuard keeps contestants out of the UI; every endpoint behind it
      // re-checks the role server-side, so the guard is convenience, not the security boundary.
      {
        path: 'setter/calls',
        component: ContestCallsComponent,
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      {
        path: 'setter/problems',
        component: ProblemManageComponent,
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      {
        path: 'setter/problems/new',
        component: ProblemEditorComponent,
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      {
        path: 'setter/problems/:slug/edit',
        component: ProblemEditorComponent,
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },
      {
        path: 'setter/problems/:slug/testcases',
        component: TestCaseEditorComponent,
        canActivate: [roleGuard],
        data: { roles: STAFF_ROLES }
      },

      { path: 'about', component: AboutComponent },
      // Home is the post-login index (it used to redirect to /problems). pathMatch 'full' keeps
      // an empty-path component route from matching every URL as a zero-segment prefix.
      { path: '', component: LandingComponent, pathMatch: 'full' },
      // Unknown URLs render a real 404 inside the shell (keeps rail + topbar).
      // Must stay LAST among the shell children so concrete paths match first.
      { path: '**', component: NotFoundComponent }
    ]
  }
];
