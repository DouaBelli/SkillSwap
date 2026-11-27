import { Injectable, computed, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class Session {
  private readonly tokenSignal = signal<string | null>(localStorage.getItem('token'));
  private readonly nameSignal = signal<string | null>(localStorage.getItem('userName'));
  private readonly roleSignal = signal<string | null>(localStorage.getItem('role'));

  readonly token = this.tokenSignal.asReadonly();
  readonly userName = this.nameSignal.asReadonly();
  readonly role = this.roleSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.tokenSignal() !== null);
  readonly isAdmin = computed(() => this.roleSignal() === 'ADMIN');

  setSession(token: string, userName: string, role = 'STUDENT'): void {
    localStorage.setItem('token', token);
    localStorage.setItem('userName', userName);
    localStorage.setItem('role', role);
    this.tokenSignal.set(token);
    this.nameSignal.set(userName);
    this.roleSignal.set(role);
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('userName');
    localStorage.removeItem('role');
    this.tokenSignal.set(null);
    this.nameSignal.set(null);
    this.roleSignal.set(null);
  }
}