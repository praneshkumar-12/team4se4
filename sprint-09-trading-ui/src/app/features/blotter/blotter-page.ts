import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ZardCardImports } from '@/shared/components/card/card.imports';

/** Placeholder — Person 5 (SEC4-640) replaces this with the real blotter. */
@Component({
  selector: 'app-blotter-page',
  imports: [ZardCardImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <z-card class="m-6 max-w-md">
      <z-card-header>
        <h2 z-card-title zTitle="Blotter"></h2>
        <p z-card-description zDescription="Person 5 builds SEC4-640 here."></p>
      </z-card-header>
    </z-card>
  `,
})
export class BlotterPage {}
