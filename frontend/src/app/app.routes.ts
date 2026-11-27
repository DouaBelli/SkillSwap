import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';

import { BrowseOffers } from './pages/browse-offers/browse-offers';
import { OfferDetails } from './pages/offer-details/offer-details';
import { PostOffer } from './pages/post-offer/post-offer';

import { Register } from './pages/register/register';
import { Verify } from './pages/verify/verify';
import { Login } from './pages/login/login';

import { MyActivity } from './features/my-activity/my-activity';
import { RequestHelpPreview } from './features/offers/components/request-help-preview/request-help-preview';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'offers' },

  // Authentication
  { path: 'register', component: Register },
  { path: 'verify', component: Verify },
  { path: 'login', component: Login },

  // Offers
  { path: 'offers', component: BrowseOffers, canActivate: [authGuard] },
  { path: 'offers/new', component: PostOffer, canActivate: [authGuard] },
  { path: 'offers/:id/edit', component: PostOffer, canActivate: [authGuard] },
  { path: 'offers/:id', component: OfferDetails, canActivate: [authGuard] },

  // My Activity and Request Help
  { path: 'my-activity', component: MyActivity, canActivate: [authGuard] },
  { path: 'request-help', component: RequestHelpPreview, canActivate: [authGuard] },
];
