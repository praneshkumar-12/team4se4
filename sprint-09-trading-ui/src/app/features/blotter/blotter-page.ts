import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, InjectionToken, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { interval, of } from 'rxjs';

import { AccountContext } from '@/core/auth/account-context';
import { ErrorMessageService } from '@/core/errors/error-message.service';
import { AccountsService } from '@/generated/trade-api/api/accounts.service';
import type { ErrorResponse } from '@/generated/trade-api/model/errorResponse';
import type { OrderHistoryEntry } from '@/generated/trade-api/model/orderHistoryEntry';
import { OrderStatus } from '@/generated/trade-api/model/orderStatus';
import { ZardBadgeComponent, type ZardBadgeTypeVariants } from '@/shared/components/badge';
import { ZardButtonComponent } from '@/shared/components/button/button.component';
import { ZardCardImports } from '@/shared/components/card/card.imports';
import { ZardSkeletonComponent } from '@/shared/components/skeleton/skeleton.component';
import { ZardTableImports } from '@/shared/components/table';

/**
 * How often the blotter re-reads order history while anything is still NEW.
 * An injection token (rather than a bare constant) so tests can override it
 * to a small value instead of faking global timers against the zoneless
 * test harness.
 */
export const BLOTTER_POLL_INTERVAL_MS = new InjectionToken<number>('BLOTTER_POLL_INTERVAL_MS', {
  providedIn: 'root',
  factory: () => 5000,
});

const STATUS_BADGE: Record<OrderStatus, { label: string; variant: ZardBadgeTypeVariants }> = {
  [OrderStatus.New]: { label: 'New', variant: 'secondary' },
  [OrderStatus.Filled]: { label: 'Filled', variant: 'success' },
  [OrderStatus.Rejected]: { label: 'Rejected', variant: 'destructive' },
  [OrderStatus.Cancelled]: { label: 'Cancelled', variant: 'secondary' },
};

function sortNewestFirst(orders: readonly OrderHistoryEntry[]): OrderHistoryEntry[] {
  return [...orders].sort((a, b) => new Date(b.createdOn).getTime() - new Date(a.createdOn).getTime());
}

function extractApiError(error: unknown): { errorCode: string | null; httpStatus: number } {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ErrorResponse> | null;
    return { errorCode: body?.errorCode ?? null, httpStatus: error.status };
  }
  return { errorCode: null, httpStatus: 0 };
}

/**
 * SEC4-640 — order blotter, with a bounded re-read while anything is NEW.
 *
 * The blotter only ever reads order history; it never places or retries an
 * order, so the idempotency-key replay concern (SEC4-640's task list) does
 * not apply here — that guard lives in the order ticket (SEC4-638), the one
 * place this app ever calls `POST /api/v1/orders`.
 */
@Component({
  selector: 'app-blotter-page',
  imports: [DatePipe, ...ZardCardImports, ...ZardTableImports, ZardBadgeComponent, ZardButtonComponent, ZardSkeletonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './blotter-page.html',
})
export class BlotterPage {
  private readonly accountContext = inject(AccountContext);
  private readonly accountsService = inject(AccountsService);
  private readonly errorMessageService = inject(ErrorMessageService);
  private readonly pollIntervalMs = inject(BLOTTER_POLL_INTERVAL_MS);

  protected readonly statusBadge = STATUS_BADGE;

  private readonly refreshTick = signal(0);

  private readonly ordersResource = rxResource({
    params: () => ({ accountId: this.accountContext.accountId(), tick: this.refreshTick() }),
    stream: ({ params }) => {
      if (params.accountId === null) {
        return of<OrderHistoryEntry[]>([]);
      }
      return this.accountsService.getOrders({ id: params.accountId });
    },
  });

  protected readonly orders = computed<OrderHistoryEntry[]>(() => sortNewestFirst(this.ordersResource.value() ?? []));

  protected readonly hasWorkingOrders = computed(() => this.orders().some((order) => order.status === OrderStatus.New));

  protected readonly isInitialLoad = computed(
    () => this.ordersResource.isLoading() && this.ordersResource.value() === undefined,
  );

  protected readonly loadError = computed<string | null>(() => {
    const error = this.ordersResource.error();
    if (!error) {
      return null;
    }
    const { errorCode, httpStatus } = extractApiError(error);
    return this.errorMessageService.getMessage(errorCode, httpStatus);
  });

  constructor() {
    // Re-reads order history on a bounded interval only while something is
    // still working. The effect's cleanup tears the subscription down the
    // moment `hasWorkingOrders` goes false (or the component is destroyed),
    // so this is never a bare `setInterval` left running after everything
    // has settled.
    effect((onCleanup) => {
      if (!this.hasWorkingOrders()) {
        return;
      }
      const subscription = interval(this.pollIntervalMs).subscribe(() => this.refresh());
      onCleanup(() => subscription.unsubscribe());
    });
  }

  protected refresh(): void {
    this.refreshTick.update((tick) => tick + 1);
  }
}
