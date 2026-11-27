import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { BrowseOffers } from './pages/browse-offers/browse-offers';
import { OfferDetails } from './pages/offer-details/offer-details';
import { PostOffer } from './pages/post-offer/post-offer';

export const routes: Routes = [{ path: '', pathMatch: 'full', redirectTo: 'offers' },
  { path: 'offers', component: BrowseOffers, canActivate: [authGuard] },
  { path: 'offers/new', component: PostOffer, canActivate: [authGuard] },
  { path: 'offers/:id/edit', component: PostOffer, canActivate: [authGuard] },
  { path: 'offers/:id', component: OfferDetails, canActivate: [authGuard] }];
