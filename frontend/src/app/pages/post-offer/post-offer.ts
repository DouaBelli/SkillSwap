import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Option, errorMessage } from '../../core/api';
import { OfferApi } from '../../offers/offer-api';
import { OfferBody } from '../../offers/offer-models';

/** Post offer. The same screen is used to edit an offer (route offers/:id/edit). */
@Component({
  selector: 'app-post-offer',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './post-offer.html',
})
export class PostOffer {
  private readonly api = inject(OfferApi);
  private readonly router = inject(Router);

  /** null when posting a new offer */
  protected readonly editId: number | null = (() => {
    const id = inject(ActivatedRoute).snapshot.paramMap.get('id');
    return id ? Number(id) : null;
  })();

  protected readonly categories = signal<Option[]>([]);
  protected readonly error = signal('');
  protected readonly saving = signal(false);

  protected readonly form = new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    categoryId: new FormControl<number | null>(null, [Validators.required]),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(1000)],
    }),
    availability: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    helpMethod: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
  });

  constructor() {
    this.api.categories().subscribe({
      next: (c) => this.categories.set(c),
      error: (e) => this.error.set(errorMessage(e)),
    });

    if (this.editId !== null) {
      this.api.get(this.editId).subscribe({
        next: (o) => {
          if (!o.mine || o.status !== 'ACTIVE') {
            this.error.set('You can only change your own active offers.');
            return;
          }
          this.form.patchValue({
            title: o.title,
            categoryId: o.categoryId,
            description: o.description,
            availability: o.availability,
            helpMethod: o.helpMethod,
          });
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
    }
  }

  protected showError(name: 'title' | 'categoryId' | 'description' | 'availability' | 'helpMethod'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body: OfferBody = {
      title: value.title,
      categoryId: value.categoryId as number,
      description: value.description,
      availability: value.availability,
      helpMethod: value.helpMethod,
    };

    this.saving.set(true);
    this.error.set('');
    const call = this.editId === null ? this.api.create(body) : this.api.update(this.editId, body);
    call.subscribe({
      next: (r) => {
        this.saving.set(false);
        this.router.navigate(['/offers', r.offer.offerId]);
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
