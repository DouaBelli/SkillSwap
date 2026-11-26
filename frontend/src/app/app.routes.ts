import { Routes } from '@angular/router';
import { MyActivity } from './features/my-activity/my-activity';
import { RequestHelpPreview } from './features/offers/components/request-help-preview/request-help-preview';

export const routes: Routes = [
  {
    path: 'my-activity',
    component: MyActivity,
  },
  {
    path: 'request-help',
    component: RequestHelpPreview,
  },
  {
    path: '',
    redirectTo: 'my-activity',
    pathMatch: 'full',
  },
];
