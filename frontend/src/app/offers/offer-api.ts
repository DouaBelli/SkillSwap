import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL, Option } from '../core/api';
import { OfferBody, OfferFilters, OfferResult, OfferView } from './offer-models';

/** Every call of the offers screens to the backend. */
@Injectable({ providedIn: 'root' })
export class OfferApi {
  private readonly http = inject(HttpClient);

  categories(): Observable<Option[]> {
    return this.http.get<Option[]>(`${API_URL}/categories`);
  }

  departments(): Observable<Option[]> {
    return this.http.get<Option[]>(`${API_URL}/departments`);
  }

  list(filters: OfferFilters): Observable<OfferView[]> {
    let params = new HttpParams();
    if (filters.categoryId !== null) params = params.set('categoryId', filters.categoryId);
    if (filters.departmentId !== null) params = params.set('departmentId', filters.departmentId);
    if (filters.keyword.trim()) params = params.set('keyword', filters.keyword.trim());
    return this.http.get<OfferView[]>(`${API_URL}/offers`, { params });
  }

  get(id: number): Observable<OfferView> {
    return this.http.get<OfferView>(`${API_URL}/offers/${id}`);
  }

  create(body: OfferBody): Observable<OfferResult> {
    return this.http.post<OfferResult>(`${API_URL}/offers`, body);
  }

  update(id: number, body: OfferBody): Observable<OfferResult> {
    return this.http.put<OfferResult>(`${API_URL}/offers/${id}`, body);
  }

  hide(id: number): Observable<OfferResult> {
    return this.http.post<OfferResult>(`${API_URL}/offers/${id}/hide`, {});
  }

  /** Volunteering flow (Person 3's endpoint): POST /api/requests */
  requestHelp(offerId: number, message: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${API_URL}/requests`, {
      offerId,
      message: message.trim() || null,
    });
  }
}
