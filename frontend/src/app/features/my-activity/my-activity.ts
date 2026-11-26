import { Component, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';

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
export class MyActivity {
  activeTab = signal<'sent' | 'received'>('sent');

  showCancelDialog = signal(false);
  selectedRequest: ActivityRequest | null = null;
  cancelReason = '';

  sentRequests: ActivityRequest[] = [
    {
      requestId: 1,
      offerTitle: 'Java Programming Help',
      studentName: 'Ahmed Mohamed',
      message: 'I need help understanding OOP concepts in Java.',
      status: 'PENDING',
      role: 'REQUESTER',
      actions: ['CANCEL'],
    },
    {
      requestId: 2,
      offerTitle: 'Database Fundamentals',
      studentName: 'Sara Ali',
      message: 'I need help with SQL queries and joins.',
      status: 'PERFORMED',
      role: 'REQUESTER',
      actions: ['CONFIRM', 'CANCEL'],
    },
  ];

  receivedRequests: ActivityRequest[] = [
    {
      requestId: 3,
      offerTitle: 'Python Programming',
      studentName: 'Mariam Hassan',
      message: 'Can you help me understand Python functions?',
      status: 'PENDING',
      role: 'VOLUNTEER',
      actions: ['ACCEPT', 'REJECT', 'CANCEL'],
    },
    {
      requestId: 4,
      offerTitle: 'Web Development',
      studentName: 'Omar Khaled',
      message: 'I need help with HTML and CSS.',
      status: 'ACCEPTED',
      role: 'VOLUNTEER',
      actions: ['PERFORM', 'CANCEL'],
    },
  ];

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

  handleAction(request: ActivityRequest, action: string): void {
    if (action === 'ACCEPT') {
      request.status = 'ACCEPTED';
      request.actions = ['PERFORM', 'CANCEL'];
    } else if (action === 'PERFORM') {
      request.status = 'PERFORMED';
      request.actions = ['CONFIRM', 'CANCEL'];
    } else if (action === 'CONFIRM') {
      request.status = 'CONFIRMED';
      request.actions = [];
      console.log('Request confirmed:', request.requestId);
    } else if (action === 'REJECT') {
      request.status = 'REJECTED';
      request.actions = [];
      console.log('Request rejected:', request.requestId);
    } else if (action === 'CANCEL') {
      this.selectedRequest = request;
      this.cancelReason = '';
      this.showCancelDialog.set(true);
    }
  }

  closeCancelDialog(): void {
    this.showCancelDialog.set(false);
    this.selectedRequest = null;
    this.cancelReason = '';
  }

  confirmCancel(): void {
    if (!this.cancelReason.trim() || !this.selectedRequest) {
      return;
    }

    this.selectedRequest.status = 'CANCELLED';
    this.selectedRequest.actions = [];

    console.log('Request cancelled:', this.selectedRequest.requestId);
    console.log('Reason:', this.cancelReason);

    this.closeCancelDialog();
  }
}
