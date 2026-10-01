import { Injectable } from '@angular/core';
import { UNKNOWN_ERROR_MESSAGE, UNREACHABLE_MESSAGE, messageForCode } from './error-catalogue';

/**
 * Turns an error response into a sentence a trader can act on. Callers
 * pass the `errorCode` from the response body and the HTTP status; the
 * message is chosen by code, never by the server's message text.
 */
@Injectable({ providedIn: 'root' })
export class ErrorMessageService {
  getMessage(errorCode: string | null, httpStatus: number): string {
    if (httpStatus === 0) {
      return UNREACHABLE_MESSAGE;
    }
    return messageForCode(errorCode) ?? UNKNOWN_ERROR_MESSAGE;
  }
}
