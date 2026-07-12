import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/**
 * Placeholder rendered inside the shell for sections whose real feature page
 * has not shipped yet. Each route supplies its section name via route `data`,
 * so teammates only swap the route's `component` when their page is ready —
 * no shell changes needed.
 */
@Component({
  selector: 'app-coming-soon',
  standalone: true,
  template: `
    <section class="coming-soon">
      <h1>{{ title }}</h1>
      <p>This section is under construction and will land soon.</p>
    </section>
  `,
  styles: [`
    .coming-soon {
      max-width: 640px;
      margin: 4rem auto;
      text-align: center;
      color: var(--text-muted);
    }
    .coming-soon h1 {
      color: var(--text-color);
      margin-bottom: 0.5rem;
    }
  `]
})
export class ComingSoonComponent {
  private route = inject(ActivatedRoute);
  readonly title: string = this.route.snapshot.data['title'] ?? 'Coming soon';
}
