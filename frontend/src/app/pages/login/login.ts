import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api';
import { AuthApi } from '../../core/auth-api';
import { Session } from '../../core/session';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  template: `
    <h1>Log in</h1>
    @if (verified) { <p class="success">Email verified. You can log in now.</p> }
    <form (ngSubmit)="submit()">
      <label>Email <input type="email" name="email" [(ngModel)]="email" required /></label>
      <label>Password <input type="password" name="password" [(ngModel)]="password" required /></label>
      @if (error()) { <p class="error">{{ error() }}</p> }
      <button type="submit" [disabled]="busy()">Log in</button>
    </form>
    <p><a routerLink="/register">Create an account</a></p>
  `,
})
export class Login {
  private readonly api = inject(AuthApi);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  verified = !!inject(ActivatedRoute).snapshot.queryParamMap.get('verified');

  email = '';
  password = '';
  error = signal('');
  busy = signal(false);

  submit() {
    this.busy.set(true);
    this.error.set('');
    this.api.login(this.email, this.password).subscribe({
      next: (r) => {
        this.session.setSession(r.token, r.fullName, r.role);
        this.router.navigate([r.role === 'ADMIN' ? '/admin' : '/']);
      },
      error: (e) => {
        if (e instanceof HttpErrorResponse && e.error?.message === 'NOT_VERIFIED') {
          this.router.navigate(['/verify'], { queryParams: { email: this.email } });
        } else {
          this.error.set(errorMessage(e));
        }
        this.busy.set(false);
      },
    });
  }
}