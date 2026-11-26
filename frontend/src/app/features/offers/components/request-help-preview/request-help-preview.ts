import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RequestHelp } from '../request-help/request-help';
import { RequestService } from '../../../../core/services/request/request.service';

@Component({
  selector: 'app-request-help-preview',
  imports: [RequestHelp],
  templateUrl: './request-help-preview.html',
  styleUrl: './request-help-preview.css',
})
export class RequestHelpPreview {
  private readonly requestService = inject(RequestService);

  showDialog = true;
  submittedMessage = '';
  errorMessage = '';
  loading = false;

  onRequestSubmitted(event: { offerId: number; message: string }): void {
    this.loading = true;
    this.errorMessage = '';

    this.requestService.createRequest(event).subscribe({
      next: (response) => {
        this.submittedMessage = response.message;
        this.showDialog = false;
        this.loading = false;
      },
      error: (error: HttpErrorResponse) => {
        console.error('Request submission error:', error);
        this.errorMessage = error.error?.message ?? 'Could not submit your request.';
        this.loading = false;
      },
    });
  }

  closeDialog(): void {
    this.showDialog = false;
  }

  openDialog(): void {
    this.submittedMessage = '';
    this.errorMessage = '';
    this.showDialog = true;
  }
}
