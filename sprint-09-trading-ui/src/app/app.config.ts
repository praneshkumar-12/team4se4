import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';
import { provideZard } from '@/shared/core/provider/providezard';
import { environment } from './core/config/environment';
import { provideApi as provideAuthApi } from './generated/auth-api/provide-api';
import { provideApi as provideTradeApi } from './generated/trade-api/provide-api';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideZard(),
    // SEC4-636 (Person 3): the only edit expected here is wrapping this in
    // `withInterceptors([bearerTokenInterceptor])` — nothing else in this
    // file should need to change for that ticket.
    provideHttpClient(),
    provideAuthApi(environment.apiBaseUrls.auth),
    provideTradeApi(environment.apiBaseUrls.trade),
  ],
};
