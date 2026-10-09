import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeZh from '@angular/common/locales/zh';
import { routes } from './app.routes';
import { authInterceptor } from './core/api/auth.interceptor';
import { HttpTeachersApiService, TeachersApi } from './core/api/teachers-api';
import { MockTeachersApiService } from './core/api/mock-teachers-api';

registerLocaleData(localeZh);

/**
 * 教师检索数据源开关（PHASE 4）：
 * true  = Mock（20 名内置教师，联调与演示）；
 * false = HTTP（GET /api/v1/matching/teachers:search，需登录态）。
 * 切换只改此一行，页面与组件零改动。
 */
const USE_MOCK_TEACHERS_API = true;

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: LOCALE_ID, useValue: 'zh' },
    {
      provide: TeachersApi,
      useClass: USE_MOCK_TEACHERS_API ? MockTeachersApiService : HttpTeachersApiService,
    },
  ],
};
