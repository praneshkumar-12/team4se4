import { TestBed } from '@angular/core/testing';
import { ErrorMessageService } from './error-message.service';

describe('ErrorMessageService (Person 1 placeholder — see SEC4-639)', () => {
  let service: ErrorMessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ErrorMessageService);
  });

  it('never throws and always returns a non-empty string for any code', () => {
    for (const code of ['ACC-404', 'MADE-UP-CODE', null]) {
      const message = service.getMessage(code, 422);
      expect(typeof message).toBe('string');
      expect(message.length).toBeGreaterThan(0);
    }
  });

  it('gives a distinct message for status 0 (unreachable / CORS)', () => {
    const unreachable = service.getMessage(null, 0);
    const other = service.getMessage(null, 422);
    expect(unreachable).not.toBe(other);
  });
});
