import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <main style="max-width: 720px; margin: 4rem auto; padding: 0 1rem;">
      <h1>Praetor</h1>
      <p>Mini online judge — skeleton is up. Build features into their modules.</p>
      <router-outlet />
    </main>
  `,
})
export class AppComponent {}
