/**
 * The one place a route path is allowed to be a string literal. Every
 * other file — the guard, the sign-in redirect, nav links — imports this
 * instead of typing `/sign-in` again.
 */
export const ROUTE_PATHS = {
  signIn: '/sign-in',
  orderTicket: '/orders/new',
  blotter: '/blotter',
} as const;

export type RoutePath = (typeof ROUTE_PATHS)[keyof typeof ROUTE_PATHS];
