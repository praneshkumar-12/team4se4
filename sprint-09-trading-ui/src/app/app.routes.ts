import { Routes } from '@angular/router';
import { ROUTE_PATHS } from './core/config/routes.const';

/**
 * Guard placeholders: Person 2 (SEC4-637) adds `canActivate: [authGuard]`
 * to every route below except `signIn` — keep that diff to just adding the
 * guard, this file's shape otherwise stays as Person 1 left it.
 */
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
    loadComponent: () =>
      import('./features/order-ticket/order-ticket-page').then((m) => m.OrderTicketPage),
  },
  {
    path: ROUTE_PATHS.blotter.slice(1),
    loadComponent: () => import('./features/blotter/blotter-page').then((m) => m.BlotterPage),
  },
];
