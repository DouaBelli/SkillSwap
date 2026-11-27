import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { errorMessage } from '../../core/api';
import { AuthApi } from '../../core/auth-api';

@Component({
  selector: 'app-verify',
  imports: [FormsModule],
  template: `
    <h1>Verify your email</h1>
    <p>Enter the 6-digit code for {{ email }}.</p>
    <form (ngSubmit)="submit()">
      <input name="code" [(ngModel)]="code" maxlength="6" inputmode="numeric"
             placeholder="000000" required />
      @if (error()) { <p class="error">{{ error() }}</p> }
      @if (info()) { <p class="success">{{ info() }}</p> }
      <button type="submit" [disabled]="busy()">Verify</button>
    </form>
    <button type="button" (click)="resend()" [disabled]="wait() > 0">
      {{ wait() > 0 ? 'Resend code in ' + wait() + 's' : 'Resend code' }}
    </button>
  `,
})
export class Verify implements OnInit, OnDestroy {
  private readonly api = inject(AuthApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  email = '';
  code = '';
  error = signal('');
  info = signal('');
  busy = signal(false);
  wait = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  ngOnInit() {
    const q = this.route.snapshot.queryParamMap;
    this.email = q.get('email') ?? '';
    if (q.get('new')) this.startWait(60); // a code was just sent
  }

  ngOnDestroy() { clearInterval(this.timer); }

  private startWait(seconds: number) {
    clearInterval(this.timer);
    this.wait.set(seconds);
    this.timer = setInterval(() => {
      this.wait.update((w) => w - 1);
      if (this.wait() <= 0) clearInterval(this.timer);
    }, 1000);
  }

  submit() {
    this.busy.set(true);
    this.error.set('');
    this.api.verify(this.email, this.code.trim()).subscribe({
      next: () => this.router.navigate(['/login'], { queryParams: { verified: 1 } }),
      error: (e) => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }

  resend() {
    this.error.set('');
    this.api.resend(this.email).subscribe({
      next: (r) => { this.info.set(r.message); this.startWait(60); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}