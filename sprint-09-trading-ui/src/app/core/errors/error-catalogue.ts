import { ErrorResponse as AuthErrorResponse } from '../../generated/auth-api';
import { ErrorResponse as TradeErrorResponse } from '../../generated/trade-api';

/**
 * Every `errorCode` either contract can return. Derived from the generated
 * clients, so regenerating them after a contract change adds a member here.
 */
export type ErrorCode = AuthErrorResponse.ErrorCodeEnum | TradeErrorResponse.ErrorCodeEnum;

/**
 * Typed as a full Record so the compiler refuses to build when a code from
 * either contract has no message.
 */
const MESSAGES: Record<ErrorCode, string> = {
  'ACC-404': 'We could not find that trading account. Check the account and try again.',
  'ACC-403':
    'This account is not active, or your sign-in does not give you access to it. Sign in with the right account or contact support.',
  'INS-404':
    'That instrument does not exist or cannot be traded right now. Check the symbol and try again.',
  'ORD-400':
    'Your cash balance is too low for this order. Lower the quantity or price, or add funds, then try again.',
  'ORD-409':
    'This order could not go through. You may not hold enough of the instrument to sell, the order may already have been submitted, or it can no longer be cancelled. Check your holdings and recent orders.',
  'VAL-422': 'Some of the details you entered are invalid. Check the fields and try again.',
  'AUTH-401': 'Your sign-in was not accepted or your session has ended. Sign in again.',
  'AUTH-409': 'That username is already taken. Choose a different one.',
};

export const UNREACHABLE_MESSAGE =
  'We could not reach the trading platform. The service may be down, or the browser blocked the request. Check your connection and try again.';

export const UNKNOWN_ERROR_MESSAGE =
  'Something went wrong that we do not recognise. Try again, and contact support if it keeps happening.';

export function messageForCode(code: string | null): string | null {
  return code !== null && Object.hasOwn(MESSAGES, code) ? MESSAGES[code as ErrorCode] : null;
}
