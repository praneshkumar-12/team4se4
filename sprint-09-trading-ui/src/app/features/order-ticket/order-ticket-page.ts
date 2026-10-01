import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  type AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  type ValidationErrors,
  Validators,
} from '@angular/forms';

import { AccountContext } from '@/core/auth/account-context';
import { ErrorMessageService } from '@/core/errors/error-message.service';
import { OrdersService } from '@/generated/trade-api/api/orders.service';
import type { ErrorResponse } from '@/generated/trade-api/model/errorResponse';
import type { OrderResponse } from '@/generated/trade-api/model/orderResponse';
import { OrderSide } from '@/generated/trade-api/model/orderSide';
import type { PlaceOrderRequest } from '@/generated/trade-api/model/placeOrderRequest';
import { ZardAlertComponent } from '@/shared/components/alert/alert.component';
import { ZardButtonComponent } from '@/shared/components/button/button.component';
import { ZardCardImports } from '@/shared/components/card/card.imports';
import { ZardInputComponent } from '@/shared/components/input/input.component';
import { ZardSelectImports } from '@/shared/components/select/select.imports';
import { ZardSkeletonComponent } from '@/shared/components/skeleton/skeleton.component';

/** Quantity must be a whole unit above zero (business rule 4). */
function wholePositiveQuantityValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === undefined || value === '') {
    return null;
  }
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? null : { wholePositiveQuantity: true };
}

/** Price must be above zero with at most two decimal places (business rule 5). */
function positivePriceValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === undefined || value === '') {
    return null;
  }
  if (typeof value !== 'number' || !(value > 0)) {
    return { positivePrice: true };
  }
  const decimalPlaces = value.toString().split('.')[1]?.length ?? 0;
  return decimalPlaces <= 2 ? null : { maxTwoDecimalPlaces: true };
}

function describeOutcome(response: OrderResponse): string {
  return `Order ${response.orderId} is ${response.status.toLowerCase()}. ${response.message}`;
}

function sideLabel(side: OrderSide): string {
  return side.charAt(0) + side.slice(1).toLowerCase();
}

function extractApiError(error: unknown): { errorCode: string | null; httpStatus: number } {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ErrorResponse> | null;
    return { errorCode: body?.errorCode ?? null, httpStatus: error.status };
  }
  return { errorCode: null, httpStatus: 0 };
}

@Component({
  selector: 'app-order-ticket-page',
  imports: [
    ReactiveFormsModule,
    ...ZardCardImports,
    ...ZardSelectImports,
    ZardInputComponent,
    ZardButtonComponent,
    ZardAlertComponent,
    ZardSkeletonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-ticket-page.html',
})
export class OrderTicketPage {
  private readonly accountContext = inject(AccountContext);
  private readonly ordersService = inject(OrdersService);
  private readonly errorMessageService = inject(ErrorMessageService);

  protected readonly accountId = this.accountContext.accountId;
  protected readonly sideOptions = Object.values(OrderSide);
  protected readonly sideLabel = sideLabel;

  protected readonly form = new FormGroup({
    symbol: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(20)],
    }),
    side: new FormControl<OrderSide | ''>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    quantity: new FormControl<number | null>(null, {
      validators: [Validators.required, wholePositiveQuantityValidator],
    }),
    price: new FormControl<number | null>(null, {
      validators: [Validators.required, positivePriceValidator],
    }),
  });

  private readonly pendingRequest = signal<PlaceOrderRequest | undefined>(undefined);

  private readonly orderResource = rxResource({
    params: () => this.pendingRequest(),
    stream: ({ params }) => this.ordersService.placeOrder({ placeOrderRequest: params }),
  });

  protected readonly isSubmitting = this.orderResource.isLoading;

  protected readonly outcome = computed<{ type: 'success' | 'error'; message: string } | null>(() => {
    const status = this.orderResource.status();
    if (status === 'resolved') {
      const response = this.orderResource.value();
      return response ? { type: 'success', message: describeOutcome(response) } : null;
    }
    if (status === 'error') {
      const { errorCode, httpStatus } = extractApiError(this.orderResource.error());
      return { type: 'error', message: this.errorMessageService.getMessage(errorCode, httpStatus) };
    }
    return null;
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }

    const { symbol, side, quantity, price } = this.form.getRawValue();
    this.pendingRequest.set({
      accountId,
      symbol,
      side: side as OrderSide,
      quantity: quantity as number,
      price: price as number,
      idempotencyKey: crypto.randomUUID(),
    });
  }
}
