import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-request-help',
  imports: [FormsModule],
  templateUrl: './request-help.html',
  styleUrl: './request-help.css',
})
export class RequestHelp {
  @Input() offerId: number = 1;
  @Input() offerTitle: string = 'Java Programming Help';

  @Output() close = new EventEmitter<void>();

  @Output() requestSubmitted = new EventEmitter<{
    offerId: number;
    message: string;
  }>();

  message = '';

  submitRequest(): void {
    this.requestSubmitted.emit({
      offerId: this.offerId,
      message: this.message.trim(),
    });
  }

  closeDialog(): void {
    this.close.emit();
  }
}
