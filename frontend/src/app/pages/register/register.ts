import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Option, errorMessage } from '../../core/api';
import { AuthApi } from '../../core/auth-api';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  template: `
    <h1>Create account</h1>
    <form (ngSubmit)="submit()">
      <label>University email
        <input type="email" name="email" [(ngModel)]="email" required />
      </label>
      <label>Password (min 8 characters)
        <input type="password" name="password" [(ngModel)]="password" minlength="8" required />
      </label>
      <label>Full name
        <input name="fullName" [(ngModel)]="fullName" required />
      </label>
      <label>Department
        <select name="department" [(ngModel)]="departmentId" required>
          <option [ngValue]="null" disabled>Choose...</option>
          @for (d of departments(); track d.id) {
            <option [ngValue]="d.id">{{ d.name }}</option>
          }
        </select>
      </label>
      <label>Year of study
        <input type="number" name="year" [(ngModel)]="year" min="1" max="6" required />
      </label>
      @if (error()) { <p class="error">{{ error() }}</p> }
      <button type="submit" [disabled]="busy()">Register</button>
    </form>
    <p>Already have an account? <a routerLink="/login">Log in</a></p>
  `,
})
export class Register implements OnInit {
  private readonly api = inject(AuthApi);
  private readonly router = inject(Router);

  departments = signal<Option[]>([]);
  error = signal('');
  busy = signal(false);

  email = '';
  password = '';
  fullName = '';
  departmentId: number | null = null;
  year: number | null = null;

  ngOnInit() {
    this.api.departments().subscribe({
      next: (d) => this.departments.set(d),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  submit() {
    if (this.departmentId === null || this.year === null) {
      this.error.set('Please fill in all fields.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api.register({
      email: this.email, password: this.password, fullName: this.fullName,
      departmentId: this.departmentId, yearOfStudy: this.year,
    }).subscribe({
      next: () => this.router.navigate(['/verify'], { queryParams: { email: this.email, new: 1 } }),
      error: (e) => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }
}