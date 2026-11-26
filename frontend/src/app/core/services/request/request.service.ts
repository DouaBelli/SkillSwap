import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// Represents one request returned by the backend
export interface RequestView {
  requestId: number;
  offerId: number;
  offerTitle: string;

  volunteerId: number;
  volunteerName: string;

  requesterId: number;
  requesterName: string;

  message: string | null;
  status: string;
  rating: number | null;

  rejectReason: string | null;
  cancelReason: string | null;

  createdAt: string;
  acceptedAt: string | null;
  performedAt: string | null;
  confirmedAt: string | null;

  role: string;
  actions: string[];
}

// Represents the response from an action endpoint
export interface ActionResult {
  message: string;
  request: RequestView;
}

// Represents the data sent to create a request
export interface CreateRequestBody {
  offerId: number;
  message?: string;
}

// Represents the body used to reject a request
export interface RejectRequestBody {
  reason?: string;
}

// Represents the body used to confirm a request
export interface ConfirmRequestBody {
  rating?: number;
}

// Represents the body used to cancel a request
export interface CancelRequestBody {
  reason: string;
}

@Injectable({
  providedIn: 'root',
})
export class RequestService {
  private readonly http = inject(HttpClient);

  private readonly apiUrl = 'http://localhost:8080/api/requests';

  // Create a request
  createRequest(data: CreateRequestBody): Observable<ActionResult> {
    return this.http.post<ActionResult>(this.apiUrl, data);
  }

  // Get sent requests
  getSentRequests(): Observable<RequestView[]> {
    return this.http.get<RequestView[]>(`${this.apiUrl}/sent`);
  }

  // Get received requests
  getReceivedRequests(): Observable<RequestView[]> {
    return this.http.get<RequestView[]>(`${this.apiUrl}/received`);
  }

  // Accept a request
  acceptRequest(requestId: number): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.apiUrl}/${requestId}/accept`, {});
  }

  // Reject a request
  rejectRequest(requestId: number, data: RejectRequestBody = {}): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.apiUrl}/${requestId}/reject`, data);
  }

  // Mark service as performed
  performRequest(requestId: number): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.apiUrl}/${requestId}/perform`, {});
  }

  // Confirm completed service
  confirmRequest(requestId: number, data: ConfirmRequestBody = {}): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.apiUrl}/${requestId}/confirm`, data);
  }

  // Cancel a request
  cancelRequest(requestId: number, data: CancelRequestBody): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.apiUrl}/${requestId}/cancel`, data);
  }

  // Get one request by ID
  getRequest(requestId: number): Observable<RequestView> {
    return this.http.get<RequestView>(`${this.apiUrl}/${requestId}`);
  }
}
