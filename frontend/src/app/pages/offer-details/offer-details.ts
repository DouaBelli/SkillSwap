import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api';
import { OfferApi } from '../../offers/offer-api';
import { OfferView } from '../../offers/offer-models';

/** Offer details: the whole offer, Request help for students, Edit and Hide for the owner. */
@Component({
  selector: 'app-offer-details',
  imports: [RouterLink, DatePipe],
  templateUrl: './offer-details.html',
})
export class OfferDetails {
  private readonly api = inject(OfferApi);
  private readonly id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));

  protected readonly offer = signal<OfferView | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  // Request help pop-up
  protected readonly showRequest = signal(false);
  protected readonly requestMessage = signal('');
  protected readonly requestError = signal('');
  protected readonly sending = signal(false);

  constructor() {
    this.api.get(this.id).subscribe({
      next: (o) => {
        this.offer.set(o);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected openRequest(): void {
    this.requestMessage.set('');
    this.requestError.set('');
    this.showRequest.set(true);
  }

  protected closeRequest(): void {
    this.showRequest.set(false);
  }

  protected sendRequest(): void {
    this.sending.set(true);
    this.requestError.set('');
    this.api.requestHelp(this.id, this.requestMessage()).subscribe({
      next: (r) => {
        this.sending.set(false);
        this.showRequest.set(false);
        this.notice.set(r.message);
      },
      error: (e) => {
        this.sending.set(false);
        this.requestError.set(errorMessage(e));
      },
    });
  }

  protected hide(): void {
    if (!confirm('Hide this offer? Pending requests on it will be cancelled.')) {
      return;
    }
    this.error.set('');
    this.api.hide(this.id).subscribe({
      next: (r) => {
        this.offer.set(r.offer);
        this.notice.set(r.message);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}
