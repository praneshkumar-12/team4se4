import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { provideZard } from '@/shared/core/provider/providezard';
import { environment } from './core/config/environment';
import { bearerTokenInterceptor } from './core/http/bearer-token.interceptor';
import { provideApi as provideAuthApi } from './generated/auth-api/provide-api';
import { provideApi as provideTradeApi } from './generated/trade-api/provide-api';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideZard(),
    provideHttpClient(withInterceptors([bearerTokenInterceptor])),
    provideAuthApi(environment.apiBaseUrls.auth),
    provideTradeApi(environment.apiBaseUrls.trade),
  ],
};
