import { Injectable, computed, signal } from '@angular/core';

/**
 * Who is logged in. Person 1 (login) calls setSession(...) after a successful login.
 * Everything else only reads isLoggedIn() or token().
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly tokenSignal = signal<string | null>(localStorage.getItem('token'));
  private readonly nameSignal = signal<string | null>(localStorage.getItem('userName'));

  readonly token = this.tokenSignal.asReadonly();
  readonly userName = this.nameSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.tokenSignal() !== null);

  setSession(token: string, userName: string): void {
    localStorage.setItem('token', token);
    localStorage.setItem('userName', userName);
    this.tokenSignal.set(token);
    this.nameSignal.set(userName);
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('userName');
    this.tokenSignal.set(null);
    this.nameSignal.set(null);
  }
}
