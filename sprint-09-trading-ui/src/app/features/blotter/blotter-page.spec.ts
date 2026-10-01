import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AccountContext } from '@/core/auth/account-context';
import { ErrorMessageService } from '@/core/errors/error-message.service';
import { AccountsService } from '@/generated/trade-api/api/accounts.service';
import type { OrderHistoryEntry } from '@/generated/trade-api/model/orderHistoryEntry';
import { OrderSide } from '@/generated/trade-api/model/orderSide';
import { OrderStatus } from '@/generated/trade-api/model/orderStatus';

import { BLOTTER_POLL_INTERVAL_MS, BlotterPage } from './blotter-page';

/** Waits for real wall-clock time to pass, for tests that override the poll interval to a small value. */
function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function anOrder(overrides: Partial<OrderHistoryEntry>): OrderHistoryEntry {
  return {
    orderId: 'ORD-1',
    accountId: 42,
    symbol: 'ACME',
    side: OrderSide.Buy,
    quantity: 10,
    price: 12.5,
    status: OrderStatus.Filled,
    createdOn: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('BlotterPage', () => {
  let fixture: ComponentFixture<BlotterPage>;
  let component: BlotterPage;
  let getOrders: ReturnType<typeof vi.fn>;

  function createFixture(pollIntervalMs = 5000): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BlotterPage],
      providers: [
        { provide: AccountContext, useValue: { accountId: () => 42 } },
        { provide: AccountsService, useValue: { getOrders } },
        { provide: ErrorMessageService, useValue: { getMessage: vi.fn().mockReturnValue('error') } },
        { provide: BLOTTER_POLL_INTERVAL_MS, useValue: pollIntervalMs },
      ],
    });

    fixture = TestBed.createComponent(BlotterPage);
    component = fixture.componentInstance;
  }

  beforeEach(() => {
    getOrders = vi.fn().mockReturnValue(of<OrderHistoryEntry[]>([]));
    createFixture();
  });

  it('order history renders newest first including rejections', async () => {
    getOrders.mockReturnValue(
      of([
        anOrder({ orderId: 'ORD-OLDEST', createdOn: '2026-01-01T00:00:00Z', status: OrderStatus.Filled }),
        anOrder({ orderId: 'ORD-REJECTED', createdOn: '2026-01-03T00:00:00Z', status: OrderStatus.Rejected }),
        anOrder({ orderId: 'ORD-MIDDLE', createdOn: '2026-01-02T00:00:00Z', status: OrderStatus.Cancelled }),
      ]),
    );

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component['orders']().map((order) => order.orderId)).toEqual([
      'ORD-REJECTED',
      'ORD-MIDDLE',
      'ORD-OLDEST',
    ]);

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="blotter-row"]');
    expect(rows.length).toBe(3);
    expect(fixture.nativeElement.textContent).toContain('Rejected');
  });

  it('shows an order at NEW as still working', async () => {
    getOrders.mockReturnValue(of([anOrder({ orderId: 'ORD-NEW', status: OrderStatus.New })]));

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component['hasWorkingOrders']()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('New');
    expect(fixture.nativeElement.querySelector('[data-testid="order-working"]')).not.toBeNull();
  });

  it('stops the re-read once nothing is at NEW', async () => {
    getOrders.mockReturnValue(of([anOrder({ status: OrderStatus.Filled })]));
    createFixture(15);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getOrders).toHaveBeenCalledTimes(1);

    await wait(60);
    fixture.detectChanges();

    expect(getOrders).toHaveBeenCalledTimes(1);
  });

  it('keeps re-reading on a bounded interval while an order is at NEW', async () => {
    getOrders.mockReturnValue(of([anOrder({ status: OrderStatus.New })]));
    createFixture(15);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getOrders).toHaveBeenCalledTimes(1);

    await wait(60);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(getOrders.mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('empty history is handled gracefully', async () => {
    getOrders.mockReturnValue(of([]));

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component['orders']()).toEqual([]);
    expect(fixture.nativeElement.querySelector('[data-testid="blotter-empty"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="blotter-table"]')).toBeNull();
  });
});
