import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api';
import { AuthApi } from '../../core/auth-api';

@Component({
  selector: 'app-reset-password',
  imports: [FormsModule, RouterLink],
  template: `
    <h1>Reset password</h1>
    @if (step() === 1) {
      <form (ngSubmit)="sendCode()">
        <label>University email
          <input type="email" name="email" [(ngModel)]="email" required />
        </label>
        @if (error()) { <p class="error">{{ error() }}</p> }
        <button type="submit" [disabled]="busy()">Send code</button>
      </form>
    } @else {
      <p>Enter the 6-digit code for {{ email }} and choose a new password.</p>
      <form (ngSubmit)="reset()">
        <label>Code
          <input name="code" [(ngModel)]="code" maxlength="6" inputmode="numeric" required />
        </label>
        <label>New password (min 8 characters)
          <input type="password" name="newPassword" [(ngModel)]="newPassword" minlength="8" required />
        </label>
        @if (info()) { <p class="success">{{ info() }}</p> }
        @if (error()) { <p class="error">{{ error() }}</p> }
        <button type="submit" [disabled]="busy()">Change password</button>
      </form>
      <button type="button" (click)="sendCode()">Resend code</button>
    }
    <p><a routerLink="/login">Back to login</a></p>
  `,
})
export class ResetPassword {
  private readonly api = inject(AuthApi);
  private readonly router = inject(Router);

  step = signal(1);
  error = signal('');
  info = signal('');
  busy = signal(false);

  email = '';
  code = '';
  newPassword = '';

  sendCode() {
    this.busy.set(true);
    this.error.set('');
    this.info.set('');
    this.api.forgot(this.email).subscribe({
      next: (r) => { this.info.set(r.message); this.step.set(2); this.busy.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }

  reset() {
    this.busy.set(true);
    this.error.set('');
    this.api.reset(this.email, this.code.trim(), this.newPassword).subscribe({
      next: () => this.router.navigate(['/login'], { queryParams: { reset: 1 } }),
      error: (e) => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }
}