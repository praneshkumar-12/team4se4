import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, beforeEach, it, expect, vi } from 'vitest';

import { AccountContext } from '@/core/auth/account-context';
import { ErrorMessageService } from '@/core/errors/error-message.service';
import { OrdersService } from '@/generated/trade-api/api/orders.service';
import { OrderResponse } from '@/generated/trade-api/model/orderResponse';
import { OrderSide } from '@/generated/trade-api/model/orderSide';
import { OrderStatus } from '@/generated/trade-api/model/orderStatus';

import { OrderTicketPage } from './order-ticket-page';

function fillValidForm(component: OrderTicketPage): void {
  component['form'].setValue({ symbol: 'ACME', side: OrderSide.Buy, quantity: 10, price: 12.5 });
}

describe('OrderTicketPage', () => {
  let fixture: ComponentFixture<OrderTicketPage>;
  let component: OrderTicketPage;
  let placeOrder: ReturnType<typeof vi.fn>;
  let getMessage: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    placeOrder = vi.fn();
    getMessage = vi.fn().mockReturnValue('The instrument is not one that can be traded.');

    TestBed.configureTestingModule({
      imports: [OrderTicketPage],
      providers: [
        { provide: AccountContext, useValue: { accountId: () => 42 } },
        { provide: OrdersService, useValue: { placeOrder } },
        { provide: ErrorMessageService, useValue: { getMessage } },
      ],
    });

    fixture = TestBed.createComponent(OrderTicketPage);
    component = fixture.componentInstance;
  });

  it('a valid order submits and shows the returned status, including NEW', async () => {
    const response: OrderResponse = {
      orderId: 'ORD-1',
      status: OrderStatus.New,
      message: 'Working',
      symbol: 'ACME',
      side: OrderSide.Buy,
      quantity: 10,
      price: 12.5,
    };
    placeOrder.mockReturnValue(of(response));

    fillValidForm(component);
    component['submit']();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(placeOrder).toHaveBeenCalledWith({
      placeOrderRequest: expect.objectContaining({
        accountId: 42,
        symbol: 'ACME',
        side: OrderSide.Buy,
        quantity: 10,
        price: 12.5,
      }),
    });
    expect(component['outcome']()).toEqual({ type: 'success', message: 'Order ORD-1 is new. Working' });
  });

  it('an invalid quantity is blocked before submission', () => {
    component['form'].setValue({ symbol: 'ACME', side: OrderSide.Buy, quantity: 0, price: 12.5 });
    component['submit']();

    expect(placeOrder).not.toHaveBeenCalled();
    expect(component['form'].invalid).toBe(true);
  });

  it('a business-rule rejection shows the mapped message', async () => {
    placeOrder.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { errorCode: 'ORD-400', message: 'raw' } })),
    );

    fillValidForm(component);
    component['submit']();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getMessage).toHaveBeenCalledWith('ORD-400', 400);
    expect(component['outcome']()).toEqual({
      type: 'error',
      message: 'The instrument is not one that can be traded.',
    });
  });
});
