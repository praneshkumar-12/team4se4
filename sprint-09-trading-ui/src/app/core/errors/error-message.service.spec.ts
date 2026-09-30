import { TestBed } from '@angular/core/testing';
import { ErrorMessageService } from './error-message.service';
import { ErrorResponse as AuthErrorResponse } from '../../generated/auth-api';
import { ErrorResponse as TradeErrorResponse } from '../../generated/trade-api';

describe('ErrorMessageService (SEC4-639)', () => {
  let service: ErrorMessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ErrorMessageService);
  });

  it('every code in both catalogues maps to a message', () => {
    const codes = new Set<string>([
      ...Object.values(AuthErrorResponse.ErrorCodeEnum),
      ...Object.values(TradeErrorResponse.ErrorCodeEnum),
    ]);
    expect(codes.size).toBeGreaterThan(0);

    const fallback = service.getMessage('NOT-A-REAL-CODE', 500);
    for (const code of codes) {
      const message = service.getMessage(code, 400);
      expect(message.length).toBeGreaterThan(0);
      expect(message).not.toBe(fallback);
      expect(message).not.toContain(code);
    }
  });

  it('an unrecognised code falls back to a readable sentence', () => {
    for (const code of ['MADE-UP-CODE', 'constructor', '', null]) {
      const message = service.getMessage(code, 422);
      expect(message).toMatch(/^[A-Z].*\.$/);
    }
  });

  it('a request that never reached a service renders a readable sentence', () => {
    const message = service.getMessage(null, 0);
    expect(message).toMatch(/^[A-Z].*\.$/);
    expect(message).toContain('could not reach');
    expect(message).not.toBe(service.getMessage(null, 422));
  });

  it('branches on the code rather than the status', () => {
    expect(service.getMessage('ORD-409', 409)).toBe(service.getMessage('ORD-409', 200));
    expect(service.getMessage('ORD-400', 400)).not.toBe(service.getMessage('ORD-409', 400));
  });
});
