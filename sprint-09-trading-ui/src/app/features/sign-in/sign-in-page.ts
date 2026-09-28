import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ZardCardImports } from '@/shared/components/card/card.imports';

/**
 * Placeholder — Person 2 (SEC4-635) replaces this with the real sign-in
 * form. Left as a ZardUI `Card` on purpose: it's Person 1's own smoke test
 * that ZardUI is wired up correctly before anyone else depends on it.
 */
@Component({
  selector: 'app-sign-in-page',
  imports: [ZardCardImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <z-card class="m-6 max-w-md">
      <z-card-header>
        <h2 z-card-title zTitle="Sign in"></h2>
        <p z-card-description zDescription="Person 2 builds SEC4-635 here."></p>
      </z-card-header>
    </z-card>
  `,
})
export class SignInPage {}
