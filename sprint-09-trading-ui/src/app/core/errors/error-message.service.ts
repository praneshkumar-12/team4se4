import { Injectable } from '@angular/core';

/**
 * Placeholder for SEC4-639 (Person 3), who replaces the body with a real,
 * completeness-checked mapping over both contracts' `errorCode` catalogues.
 * **This public method name and signature is the contract between us** —
 * SEC4-638 (Person 4) and SEC4-640 (Person 5) already call it; Person 3's
 * merge must stay a pure enhancement, never a breaking rename.
 */
@Injectable({ providedIn: 'root' })
export class ErrorMessageService {
  getMessage(errorCode: string | null, httpStatus: number): string {
    if (httpStatus === 0) {
      return 'Could not reach the trading platform. Check your connection, or the service may be down.';
    }
    return 'Something went wrong talking to the trading platform. Try again.';
  }
}
