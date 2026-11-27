import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { API_URL, Option } from './api';

export interface LoginResponse {
  token: string;
  userId: number;
  fullName: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  departments() {
    return this.http.get<Option[]>(`${API_URL}/departments`);
  }
  register(body: {
    email: string; password: string; fullName: string;
    departmentId: number; yearOfStudy: number;
  }) {
    return this.http.post<{ message: string }>(`${API_URL}/auth/register`, body);
  }
  verify(email: string, code: string) {
    return this.http.post<{ message: string }>(`${API_URL}/auth/verify`, { email, code });
  }
  resend(email: string) {
    return this.http.post<{ message: string }>(`${API_URL}/auth/resend`, { email });
  }
  login(email: string, password: string) {
    return this.http.post<LoginResponse>(`${API_URL}/auth/login`, { email, password });
  }
}