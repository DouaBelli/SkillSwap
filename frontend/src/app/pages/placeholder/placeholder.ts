import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/**
 * Temporary page for screens that belong to someone else.
 * Whoever builds the real screen replaces the route in app.routes.ts.
 */
@Component({
  selector: 'app-placeholder',
  template: `
    <section class="page">
      <h1>{{ title }}</h1>
      <p class="muted">This screen is not built yet. Owner: {{ owner }}.</p>
    </section>
  `,
})
export class Placeholder {
  private readonly route = inject(ActivatedRoute);
  protected readonly title: string = this.route.snapshot.data['title'] ?? 'Coming soon';
  protected readonly owner: string = this.route.snapshot.data['owner'] ?? 'the team';
}
