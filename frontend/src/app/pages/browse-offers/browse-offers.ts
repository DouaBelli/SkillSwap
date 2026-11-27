import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { Option, errorMessage } from '../../core/api';
import { OfferApi } from '../../offers/offer-api';
import { OfferView } from '../../offers/offer-models';

/** Browse offers: category buttons, department filter, keyword search. Newest offers first. */
@Component({
  selector: 'app-browse-offers',
  imports: [RouterLink, DatePipe],
  templateUrl: './browse-offers.html',
  styleUrl: './browse-offers.css',
})
export class BrowseOffers {
  private readonly api = inject(OfferApi);

  protected readonly offers = signal<OfferView[]>([]);
  protected readonly categories = signal<Option[]>([]);
  protected readonly departments = signal<Option[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  protected readonly categoryId = signal<number | null>(null);
  protected readonly departmentId = signal<number | null>(null);
  protected readonly keyword = signal('');

  private readonly search$ = new Subject<void>();
  private readonly typing$ = new Subject<string>();

  constructor() {
    this.api.categories().subscribe({ next: (c) => this.categories.set(c), error: () => {} });
    this.api.departments().subscribe({ next: (d) => this.departments.set(d), error: () => {} });

    // Only the newest search counts, so a slow old answer cannot replace a newer one.
    this.search$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set('');
        }),
        switchMap(() =>
          this.api
            .list({
              categoryId: this.categoryId(),
              departmentId: this.departmentId(),
              keyword: this.keyword(),
            })
            .pipe(
              catchError((e) => {
                this.error.set(errorMessage(e));
                return of([] as OfferView[]);
              }),
            ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((offers) => {
        this.offers.set(offers);
        this.loading.set(false);
      });

    // Wait until the student stops typing before searching.
    this.typing$
      .pipe(debounceTime(300), takeUntilDestroyed())
      .subscribe((text) => {
        this.keyword.set(text);
        this.search$.next();
      });

    this.search$.next();
  }

  protected onKeyword(text: string): void {
    this.typing$.next(text);
  }

  protected pickCategory(id: number | null): void {
    this.categoryId.set(id);
    this.search$.next();
  }

  protected pickDepartment(value: string): void {
    this.departmentId.set(value ? Number(value) : null);
    this.search$.next();
  }

  protected clearFilters(): void {
    this.categoryId.set(null);
    this.departmentId.set(null);
    this.keyword.set('');
    this.search$.next();
  }

  protected hasFilters(): boolean {
    return this.categoryId() !== null || this.departmentId() !== null || this.keyword() !== '';
  }
}
