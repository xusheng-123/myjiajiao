import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeZh from '@angular/common/locales/zh';
import { routes } from './app.routes';
import { authInterceptor } from './core/api/auth.interceptor';
import { showcaseMockInterceptor } from './core/api/showcase-mock.interceptor';
import { HttpTeachersApiService, TeachersApi } from './core/api/teachers-api';
import { MockTeachersApiService } from './core/api/mock-teachers-api';

registerLocaleData(localeZh);

/**
 * 教师检索数据源开关（PHASE 4）：
 * true  = Mock（20 名内置教师，联调与演示）；
 * false = HTTP（GET /api/v1/matching/public/teachers，只展示真实已认证在售导师）。
 * 静态展示站模式下开启 Mock，确保脱机演示正常。
 */
const USE_MOCK_TEACHERS_API = true;

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, showcaseMockInterceptor])),
    { provide: LOCALE_ID, useValue: 'zh' },
    {
      provide: TeachersApi,
      useClass: USE_MOCK_TEACHERS_API ? MockTeachersApiService : HttpTeachersApiService,
    },
  ],
};
