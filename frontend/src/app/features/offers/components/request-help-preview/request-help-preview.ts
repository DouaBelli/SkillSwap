import { Component } from '@angular/core';
import { RequestHelp } from '../request-help/request-help';

@Component({
  selector: 'app-request-help-preview',
  imports: [RequestHelp],
  templateUrl: './request-help-preview.html',
  styleUrl: './request-help-preview.css',
})
export class RequestHelpPreview {
  showDialog = true;
  submittedMessage = '';

  onRequestSubmitted(event: { offerId: number; message: string }): void {
    this.submittedMessage = `Request submitted for offer #${event.offerId}. Message: ${event.message || '(No message)'}`;

    this.showDialog = false;
  }

  closeDialog(): void {
    this.showDialog = false;
  }

  openDialog(): void {
    this.submittedMessage = '';
    this.showDialog = true;
  }
}
