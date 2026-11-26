import { Component, signal, inject, OnInit } from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ActionResult,
  RequestService,
  RequestView,
} from '../../core/services/request/request.service';

interface ActivityRequest {
  requestId: number;
  offerTitle: string;
  studentName: string;
  message: string;
  status: string;
  role: 'REQUESTER' | 'VOLUNTEER';
  actions: string[];
}

@Component({
  selector: 'app-my-activity',
  imports: [NgClass, FormsModule],
  templateUrl: './my-activity.html',
  styleUrl: './my-activity.css',
})
export class MyActivity implements OnInit {
  private readonly requestService = inject(RequestService);

  activeTab = signal<'sent' | 'received'>('sent');

  showCancelDialog = signal(false);
  selectedRequest: ActivityRequest | null = null;
  cancelReason = '';

  sentRequests: ActivityRequest[] = [];
  receivedRequests: ActivityRequest[] = [];

  loading = false;
  errorMessage = '';

  // Load requests when the page opens
  ngOnInit(): void {
    this.loadRequests();
  }

  // Load both lists from Spring Boot
  loadRequests(): void {
    this.loading = true;
    this.errorMessage = '';

    this.requestService.getSentRequests().subscribe({
      next: (requests: RequestView[]) => {
        this.sentRequests = requests.map((request) => this.mapRequest(request));

        this.requestService.getReceivedRequests().subscribe({
          next: (requests: RequestView[]) => {
            this.receivedRequests = requests.map((request) => this.mapRequest(request));

            this.loading = false;
          },
          error: (error: HttpErrorResponse) => {
            console.error('Received requests error:', error);
            this.errorMessage = error.error?.message ?? 'Could not load received requests.';
            this.loading = false;
          },
        });
      },
      error: (error: HttpErrorResponse) => {
        console.error('Sent requests error:', error);
        this.errorMessage = error.error?.message ?? 'Could not load sent requests.';
        this.loading = false;
      },
    });
  }

  // Convert backend data to the fields used by the existing HTML
  private mapRequest(request: RequestView): ActivityRequest {
    return {
      requestId: request.requestId,
      offerTitle: request.offerTitle,
      studentName: request.role === 'REQUESTER' ? request.volunteerName : request.requesterName,
      message: request.message ?? '',
      status: request.status,
      role: request.role as 'REQUESTER' | 'VOLUNTEER',
      actions: request.actions ?? [],
    };
  }

  switchTab(tab: 'sent' | 'received'): void {
    this.activeTab.set(tab);
  }

  actionLabel(action: string): string {
    const labels: Record<string, string> = {
      ACCEPT: 'Accept',
      REJECT: 'Reject',
      PERFORM: 'Service Performed',
      CONFIRM: 'Confirm',
      CANCEL: 'Cancel',
    };

    return labels[action] ?? action;
  }

  actionClass(action: string): string {
    return action.toLowerCase();
  }

  // Send an action to the backend
  handleAction(request: ActivityRequest, action: string): void {
    if (action === 'CANCEL') {
      this.selectedRequest = request;
      this.cancelReason = '';
      this.showCancelDialog.set(true);
      return;
    }

    let operation: Observable<ActionResult>;

    switch (action) {
      case 'ACCEPT':
        operation = this.requestService.acceptRequest(request.requestId);
        break;

      case 'REJECT':
        operation = this.requestService.rejectRequest(request.requestId, {});
        break;

      case 'PERFORM':
        operation = this.requestService.performRequest(request.requestId);
        break;

      case 'CONFIRM':
        operation = this.requestService.confirmRequest(request.requestId, {});
        break;

      default:
        return;
    }

    operation.subscribe({
      next: (response: ActionResult) => {
        this.updateRequest(request, response.request);
      },
      error: (error: HttpErrorResponse) => {
        console.error(`Could not perform ${action}:`, error);
        alert(error.error?.message ?? `Could not ${action.toLowerCase()} request.`);
      },
    });
  }

  // Update the displayed item from the backend response
  private updateRequest(request: ActivityRequest, updatedRequest: RequestView): void {
    Object.assign(request, this.mapRequest(updatedRequest));
  }

  closeCancelDialog(): void {
    this.showCancelDialog.set(false);
    this.selectedRequest = null;
    this.cancelReason = '';
  }

  // Cancel request using the reason entered by the user
  confirmCancel(): void {
    if (!this.cancelReason.trim() || !this.selectedRequest) {
      return;
    }

    const request = this.selectedRequest;

    this.requestService
      .cancelRequest(request.requestId, { reason: this.cancelReason.trim() })
      .subscribe({
        next: (response: ActionResult) => {
          this.updateRequest(request, response.request);
          this.closeCancelDialog();
        },
        error: (error: HttpErrorResponse) => {
          console.error('Cancel request error:', error);
          alert(error.error?.message ?? 'Could not cancel request.');
        },
      });
  }
}
