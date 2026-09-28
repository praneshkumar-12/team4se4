import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ZardCardImports } from '@/shared/components/card/card.imports';

/** Placeholder — Person 4 (SEC4-638) replaces this with the real order ticket. */
@Component({
  selector: 'app-order-ticket-page',
  imports: [ZardCardImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <z-card class="m-6 max-w-md">
      <z-card-header>
        <h2 z-card-title zTitle="Order ticket"></h2>
        <p z-card-description zDescription="Person 4 builds SEC4-638 here."></p>
      </z-card-header>
    </z-card>
  `,
})
export class OrderTicketPage {}
