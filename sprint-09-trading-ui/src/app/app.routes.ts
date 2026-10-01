import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { ROUTE_PATHS } from './core/config/routes.const';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: ROUTE_PATHS.blotter.slice(1),
  },
  {
    path: ROUTE_PATHS.signIn.slice(1),
    loadComponent: () => import('./features/sign-in/sign-in-page').then((m) => m.SignInPage),
  },
  {
    path: ROUTE_PATHS.orderTicket.slice(1),
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/order-ticket/order-ticket-page').then((m) => m.OrderTicketPage),
  },
  {
    path: ROUTE_PATHS.blotter.slice(1),
    canActivate: [authGuard],
    loadComponent: () => import('./features/blotter/blotter-page').then((m) => m.BlotterPage),
  },
];
