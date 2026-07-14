import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { RegisterComponent } from './features/auth/register/register.component';
import { ShellComponent } from './layout/shell/shell.component';
import { ComingSoonComponent } from './shared/components/coming-soon/coming-soon.component';
import { ProblemListComponent } from './features/problems/problem-list/problem-list.component';
import { ProblemDetailComponent } from './features/problems/problem-detail/problem-detail.component';
import { SubmissionDetailComponent } from './features/submissions/submission-detail/submission-detail.component';
import { ContestListComponent } from './features/contests/contest-list/contest-list.component';
import { ContestDetailComponent } from './features/contests/contest-detail/contest-detail.component';
import { AboutComponent } from './features/about/about.component';
import { NotFoundComponent } from './features/not-found/not-found.component';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  // Auth pages render outside the shell (no rail/topbar).
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },

  // Everything else lives inside the authenticated shell. Section pages are
  // owned by different teammates and land incrementally; until each ships its
  // route resolves to ComingSoon. Swap `component` when the real page is ready.
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: 'problems', component: ProblemListComponent },
      { path: 'problems/:slug', component: ProblemDetailComponent },
      { path: 'submissions/:id', component: SubmissionDetailComponent },
      { path: 'contests', component: ContestListComponent },
      { path: 'contests/:id', component: ContestDetailComponent },
      // Standings are per-contest → live inside a contest's detail page.
      { path: 'standings', redirectTo: 'contests', pathMatch: 'full' },
      { path: 'submissions', component: ComingSoonComponent, data: { title: 'Submissions' } },
      { path: 'profile', component: ComingSoonComponent, data: { title: 'Profile' } },
      { path: 'about', component: AboutComponent },
      { path: '', redirectTo: 'problems', pathMatch: 'full' },
      // Unknown URLs render a real 404 inside the shell (keeps rail + topbar).
      // Must stay LAST among the shell children so concrete paths match first.
      { path: '**', component: NotFoundComponent }
    ]
  }
];
