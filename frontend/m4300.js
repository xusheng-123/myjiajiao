import { injectQuery as __vite__injectQuery } from "/@vite/client";import { createHotContext as __vite__createHotContext } from "/@vite/client";import.meta.hot = __vite__createHotContext("/main.js");import {
  FEATURED_TEACHERS
} from "/chunk-I6A5J5ZM.js";
import {
  HttpTeachersApiService,
  TEACHERS_API,
  TeachersApi
} from "/chunk-EID5BZAW.js";
import {
  AuthService,
  Role
} from "/chunk-3C5G3SBL.js";
import "/chunk-3CLT366D.js";
import "/chunk-C4KO2HLL.js";

// src/main.ts
import { bootstrapApplication } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_platform-browser.js?v=079cbba3";

// src/app/app.config.ts
import { LOCALE_ID, provideZoneChangeDetection } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_core.js?v=079cbba3";
import { provideRouter, withComponentInputBinding } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_router.js?v=079cbba3";
import { provideHttpClient, withInterceptors } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_common_http.js?v=079cbba3";
import { registerLocaleData } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_common.js?v=079cbba3";
import localeZh from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_common_locales_zh.js?v=079cbba3";

// src/app/core/auth/auth.guard.ts
import { inject } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_core.js?v=079cbba3";
import { Router } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_router.js?v=079cbba3";
function roleGuard(required) {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (auth.isLoggedIn() && auth.role() === required) {
      return true;
    }
    return router.createUrlTree(["/auth"], { queryParams: { role: required.toLowerCase() } });
  };
}

// src/app/app.routes.ts
var routes = [
  { path: "", loadComponent: () => import("/chunk-JNH2HMY7.js").then((m) => m.LandingComponent) },
  { path: "teachers", loadComponent: () => import("/chunk-LRISMVAO.js").then((m) => m.TeachersComponent) },
  { path: "auth", loadComponent: () => import("/chunk-UNFAZUS4.js").then((m) => m.AuthPortalComponent) },
  {
    path: "student",
    canActivate: [roleGuard(Role.STUDENT)],
    children: [
      { path: "", redirectTo: "dashboard", pathMatch: "full" },
      { path: "dashboard", loadComponent: () => import("/chunk-P4EI66B4.js").then((m) => m.StudentDashboardComponent) },
      { path: "match", loadComponent: () => import("/chunk-LOGPAIEA.js").then((m) => m.MatchComponent) }
    ]
  },
  {
    path: "parent",
    canActivate: [roleGuard(Role.PARENT)],
    children: [
      { path: "", redirectTo: "dashboard", pathMatch: "full" },
      { path: "dashboard", loadComponent: () => import("/chunk-DV4ZZXU3.js").then((m) => m.ParentDashboardComponent) }
    ]
  },
  {
    path: "teacher",
    canActivate: [roleGuard(Role.TEACHER)],
    children: [
      { path: "", redirectTo: "dashboard", pathMatch: "full" },
      { path: "dashboard", loadComponent: () => import("/chunk-WHUWX43C.js").then((m) => m.TeacherDashboardComponent) }
    ]
  },
  {
    path: "admin",
    canActivate: [roleGuard(Role.ADMIN)],
    children: [
      { path: "", redirectTo: "dashboard", pathMatch: "full" },
      { path: "dashboard", loadComponent: () => import("/chunk-3DLAHMZX.js").then((m) => m.AdminDashboardComponent) }
    ]
  },
  { path: "**", redirectTo: "" }
];

// src/app/core/api/auth.interceptor.ts
import { inject as inject2 } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_core.js?v=079cbba3";
var authInterceptor = (req, next) => {
  const auth = inject2(AuthService);
  const session = auth.session();
  let request = req;
  if (session) {
    request = request.clone({ setHeaders: { Authorization: `Bearer ${session.accessToken}` } });
  }
  if (["POST", "PUT", "PATCH", "DELETE"].includes(request.method)) {
    request = request.clone({ setHeaders: { "Idempotency-Key": crypto.randomUUID() } });
  }
  return next(request);
};

// src/app/core/api/mock-teachers-api.ts
import { Injectable } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_core.js?v=079cbba3";
import { of } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/rxjs.js?v=079cbba3";
import { delay } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/rxjs_operators.js?v=079cbba3";

// src/app/features/teachers/teachers.mock.ts
var ALL_TEACHERS = [
  ...FEATURED_TEACHERS,
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e07",
    displayName: "\u6C88\u89C2\u6F9C",
    school: "\u5317\u4EAC\u5927\u5B66",
    highestDegree: "\u535A\u58EB",
    yearsOfTeaching: 11,
    subject: "\u6570\u5B66",
    gradeBand: "\u9AD8\u4E2D",
    pricePerHourCents: 39800,
    verificationApproved: true,
    averageRating: 4.9,
    reviewCount: 201,
    nextOpenSlotAt: "2026-10-02T19:00:00+08:00",
    styleTags: ["\u601D\u7EF4\u63A8\u5BFC", "\u7ADE\u8D5B\u8F85\u5BFC", "\u6DF1\u5165\u6D45\u51FA"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e08",
    displayName: "\u987E\u6E05\u79BE",
    school: "\u590D\u65E6\u5927\u5B66",
    highestDegree: "\u7855\u58EB",
    yearsOfTeaching: 5,
    subject: "\u82F1\u8BED",
    gradeBand: "\u521D\u4E2D",
    pricePerHourCents: 18800,
    verificationApproved: true,
    averageRating: 4.8,
    reviewCount: 143,
    nextOpenSlotAt: "2026-09-28T19:00:00+08:00",
    styleTags: ["\u60C5\u666F\u5BF9\u8BDD", "\u5174\u8DA3\u5F15\u5BFC", "\u8010\u5FC3\u7EC6\u81F4"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e09",
    displayName: "\u9646\u95FB\u7B19",
    school: "\u4E0A\u6D77\u4EA4\u901A\u5927\u5B66",
    highestDegree: "\u7855\u58EB",
    yearsOfTeaching: 4,
    subject: "\u7269\u7406",
    gradeBand: "\u521D\u4E2D",
    pricePerHourCents: 20800,
    verificationApproved: true,
    averageRating: 4.7,
    reviewCount: 76,
    nextOpenSlotAt: "2026-09-29T18:30:00+08:00",
    styleTags: ["\u6A21\u578B\u6784\u5EFA", "\u6DF1\u5165\u6D45\u51FA", "\u542F\u53D1\u5F0F\u63D0\u95EE"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e0a",
    displayName: "\u82CF\u781A\u9752",
    school: "\u5357\u4EAC\u5927\u5B66",
    highestDegree: "\u5B66\u58EB",
    yearsOfTeaching: 3,
    subject: "\u5316\u5B66",
    gradeBand: "\u521D\u4E2D",
    pricePerHourCents: 16800,
    verificationApproved: true,
    averageRating: 4.6,
    reviewCount: 58,
    nextOpenSlotAt: "2026-10-01T20:00:00+08:00",
    styleTags: ["\u5B9E\u9A8C\u63A2\u7A76", "\u7A33\u624E\u7A33\u6253", "\u8010\u5FC3\u7EC6\u81F4"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e0b",
    displayName: "\u7A0B\u96E8\u7720",
    school: "\u6D59\u6C5F\u5927\u5B66",
    highestDegree: "\u7855\u58EB",
    yearsOfTeaching: 6,
    subject: "\u751F\u7269",
    gradeBand: "\u9AD8\u4E2D",
    pricePerHourCents: 25800,
    verificationApproved: true,
    averageRating: 4.8,
    reviewCount: 91,
    nextOpenSlotAt: "2026-09-30T18:00:00+08:00",
    styleTags: ["\u77E5\u8BC6\u7F51\u7EDC", "\u56FE\u89E3\u8BB0\u5FC6", "\u7A33\u624E\u7A33\u6253"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e0c",
    displayName: "\u6797\u8FF0\u6000",
    school: "\u6B66\u6C49\u5927\u5B66",
    highestDegree: "\u7855\u58EB",
    yearsOfTeaching: 8,
    subject: "\u5386\u53F2",
    gradeBand: "\u9AD8\u4E2D",
    pricePerHourCents: 21800,
    verificationApproved: true,
    averageRating: 4.9,
    reviewCount: 112,
    nextOpenSlotAt: "2026-09-28T20:00:00+08:00",
    styleTags: ["\u6587\u53F2\u8D2F\u901A", "\u6545\u4E8B\u5316\u6559\u5B66", "\u6587\u672C\u7EC6\u8BFB"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e0d",
    displayName: "\u6C88\u758F\u6850",
    school: "\u4E2D\u5C71\u5927\u5B66",
    highestDegree: "\u5B66\u58EB",
    yearsOfTeaching: 4,
    subject: "\u5730\u7406",
    gradeBand: "\u521D\u4E2D",
    pricePerHourCents: 15800,
    verificationApproved: true,
    averageRating: 4.5,
    reviewCount: 44,
    nextOpenSlotAt: "2026-10-03T19:00:00+08:00",
    styleTags: ["\u5730\u56FE\u601D\u7EF4", "\u6848\u4F8B\u6559\u5B66", "\u7A33\u624E\u7A33\u6253"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e0e",
    displayName: "\u987E\u5317\u8FB0",
    school: "\u54C8\u5C14\u6EE8\u5DE5\u4E1A\u5927\u5B66",
    highestDegree: "\u7855\u58EB",
    yearsOfTeaching: 7,
    subject: "\u4FE1\u606F\u5B66",
    gradeBand: "\u9AD8\u4E2D",
    pricePerHourCents: 35800,
    verificationApproved: true,
    averageRating: 4.9,
    reviewCount: 168,
    nextOpenSlotAt: "2026-09-29T19:30:00+08:00",
    styleTags: ["\u7B97\u6CD5\u601D\u7EF4", "\u9879\u76EE\u5B9E\u6218", "\u7ADE\u8D5B\u8F85\u5BFC"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e0f",
    displayName: "\u9646\u8513\u97F3",
    school: "\u5317\u4EAC\u5E08\u8303\u5927\u5B66",
    highestDegree: "\u7855\u58EB",
    yearsOfTeaching: 6,
    subject: "\u8BED\u6587",
    gradeBand: "\u521D\u4E2D",
    pricePerHourCents: 17800,
    verificationApproved: true,
    averageRating: 4.8,
    reviewCount: 87,
    nextOpenSlotAt: "2026-09-30T19:00:00+08:00",
    styleTags: ["\u6587\u672C\u7EC6\u8BFB", "\u5199\u4F5C\u601D\u7EF4", "\u966A\u4F34\u5F0F\u7763\u5BFC"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e10",
    displayName: "\u82CF\u884C\u821F",
    school: "\u5357\u5F00\u5927\u5B66",
    highestDegree: "\u5B66\u58EB",
    yearsOfTeaching: 3,
    subject: "\u6570\u5B66",
    gradeBand: "\u521D\u4E2D",
    pricePerHourCents: 14800,
    verificationApproved: true,
    averageRating: 4.4,
    reviewCount: 39,
    nextOpenSlotAt: "2026-09-28T18:00:00+08:00",
    styleTags: ["\u8003\u70B9\u62C6\u89E3", "\u9519\u9898\u7BA1\u7406", "\u7A33\u624E\u7A33\u6253"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e11",
    displayName: "\u7A0B\u77E5\u8BB8",
    school: "\u56DB\u5DDD\u5927\u5B66",
    highestDegree: "\u7855\u58EB",
    yearsOfTeaching: 5,
    subject: "\u82F1\u8BED",
    gradeBand: "\u5C0F\u5B66",
    pricePerHourCents: 12800,
    verificationApproved: true,
    averageRating: 4.7,
    reviewCount: 66,
    nextOpenSlotAt: "2026-10-01T18:30:00+08:00",
    styleTags: ["\u81EA\u7136\u62FC\u8BFB", "\u6E38\u620F\u5316\u6559\u5B66", "\u5174\u8DA3\u5F15\u5BFC"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e12",
    displayName: "\u6797\u671B\u8212",
    school: "\u534E\u4E1C\u5E08\u8303\u5927\u5B66",
    highestDegree: "\u7855\u58EB",
    yearsOfTeaching: 2,
    subject: "\u6570\u5B66",
    gradeBand: "\u5C0F\u5B66",
    pricePerHourCents: 9800,
    verificationApproved: true,
    averageRating: null,
    reviewCount: 0,
    nextOpenSlotAt: "2026-09-29T17:00:00+08:00",
    styleTags: ["\u6E38\u620F\u5316\u6559\u5B66", "\u8010\u5FC3\u7EC6\u81F4", "\u6DF1\u5165\u6D45\u51FA"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e13",
    displayName: "\u6C88\u781A\u8F9E",
    school: "\u540C\u6D4E\u5927\u5B66",
    highestDegree: "\u5B66\u58EB",
    yearsOfTeaching: 2,
    subject: "\u7269\u7406",
    gradeBand: "\u9AD8\u4E2D",
    pricePerHourCents: 23800,
    verificationApproved: false,
    averageRating: null,
    reviewCount: 0,
    nextOpenSlotAt: "2026-10-02T18:00:00+08:00",
    styleTags: ["\u8003\u70B9\u62C6\u89E3", "\u601D\u7EF4\u63A8\u5BFC"]
  },
  {
    teacherId: "01930eee-6a01-7b2e-b1c0-1a2b3c4d5e14",
    displayName: "\u987E\u9759\u59DD",
    school: "\u5317\u4EAC\u5916\u56FD\u8BED\u5927\u5B66",
    highestDegree: "\u7855\u58EB",
    yearsOfTeaching: 4,
    subject: "\u82F1\u8BED",
    gradeBand: "\u9AD8\u4E2D",
    pricePerHourCents: 28800,
    verificationApproved: false,
    averageRating: null,
    reviewCount: 0,
    nextOpenSlotAt: null,
    styleTags: ["\u60C5\u666F\u5BF9\u8BDD", "\u5199\u4F5C\u601D\u7EF4"]
  }
];

// src/app/core/api/mock-teachers-api.ts
import * as i0 from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_core.js?v=079cbba3";
var MockTeachersApiService = class _MockTeachersApiService extends TeachersApi {
  search(query) {
    const keyword = query.keyword?.trim();
    const list = ALL_TEACHERS.filter((t) => {
      if (!query.includeUnverified && !t.verificationApproved) {
        return false;
      }
      if (query.subject && !t.subject.includes(query.subject)) {
        return false;
      }
      if (query.gradeBand && t.gradeBand !== query.gradeBand) {
        return false;
      }
      if (query.maxPricePerHourCents != null && t.pricePerHourCents > query.maxPricePerHourCents) {
        return false;
      }
      if (query.styleTag && !t.styleTags.includes(query.styleTag)) {
        return false;
      }
      if (keyword) {
        const haystack = `${t.subject} ${t.gradeBand} ${t.school} ${t.displayName}`;
        if (!haystack.includes(keyword)) {
          return false;
        }
      }
      return true;
    });
    const sorted = [...list].sort((a, b) => evidenceScore(b) - evidenceScore(a));
    return of(sorted).pipe(delay(250));
  }
  static \u0275fac = /* @__PURE__ */ (() => {
    let \u0275MockTeachersApiService_BaseFactory;
    return function MockTeachersApiService_Factory(__ngFactoryType__) {
      return (\u0275MockTeachersApiService_BaseFactory || (\u0275MockTeachersApiService_BaseFactory = i0.\u0275\u0275getInheritedFactory(_MockTeachersApiService)))(__ngFactoryType__ || _MockTeachersApiService);
    };
  })();
  static \u0275prov = /* @__PURE__ */ i0.\u0275\u0275defineInjectable({ token: _MockTeachersApiService, factory: _MockTeachersApiService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i0.\u0275setClassMetadata(MockTeachersApiService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();
function evidenceScore(t) {
  let score = 0;
  if (t.verificationApproved)
    score += 2;
  if (t.reviewCount > 0)
    score += 1;
  if (t.nextOpenSlotAt)
    score += 1;
  return score;
}

// src/app/app.config.ts
registerLocaleData(localeZh);
var USE_MOCK_TEACHERS_API = true;
var appConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: LOCALE_ID, useValue: "zh" },
    {
      provide: TEACHERS_API,
      useClass: USE_MOCK_TEACHERS_API ? MockTeachersApiService : HttpTeachersApiService
    }
  ]
};

// src/app/app.component.ts
import { Component } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_core.js?v=079cbba3";
import { RouterOutlet } from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_router.js?v=079cbba3";
import * as i02 from "/@fs/E:/线上家教平台/线上家教平台（功能）/frontend/.angular/cache/19.2.27/nurture-frontend/vite/deps/@angular_core.js?v=079cbba3";
var AppComponent = class _AppComponent {
  static \u0275fac = function AppComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AppComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ i02.\u0275\u0275defineComponent({ type: _AppComponent, selectors: [["nur-root"]], decls: 4, vars: 0, consts: [["href", "#main-content", 1, "skip-link"], ["id", "main-content"]], template: function AppComponent_Template(rf, ctx) {
    if (rf & 1) {
      i02.\u0275\u0275elementStart(0, "a", 0);
      i02.\u0275\u0275text(1, "\u8DF3\u8F6C\u5230\u4E3B\u8981\u5185\u5BB9");
      i02.\u0275\u0275elementEnd();
      i02.\u0275\u0275elementStart(2, "main", 1);
      i02.\u0275\u0275element(3, "router-outlet");
      i02.\u0275\u0275elementEnd();
    }
  }, dependencies: [RouterOutlet], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i02.\u0275setClassMetadata(AppComponent, [{
    type: Component,
    args: [{
      selector: "nur-root",
      standalone: true,
      imports: [RouterOutlet],
      template: `
    <a class="skip-link" href="#main-content">\u8DF3\u8F6C\u5230\u4E3B\u8981\u5185\u5BB9</a>
    <main id="main-content">
      <router-outlet />
    </main>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && i02.\u0275setClassDebugInfo(AppComponent, { className: "AppComponent", filePath: "src/app/app.component.ts", lineNumber: 15 });
})();
(() => {
  const id = "src%2Fapp%2Fapp.component.ts%40AppComponent";
  function AppComponent_HmrLoad(t) {
    import(
      /* @vite-ignore */
      __vite__injectQuery(i02.\u0275\u0275getReplaceMetadataURL(id, t, import.meta.url), 'import')
    ).then((m) => m.default && i02.\u0275\u0275replaceMetadata(AppComponent, m.default, [i02], [RouterOutlet, Component], import.meta, id));
  }
  (typeof ngDevMode === "undefined" || ngDevMode) && AppComponent_HmrLoad(Date.now());
  (typeof ngDevMode === "undefined" || ngDevMode) && (import.meta.hot && import.meta.hot.on("angular:component-update", (d) => d.id === id && AppComponent_HmrLoad(d.timestamp)));
})();

// src/main.ts
bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));


//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbInNyYy9tYWluLnRzIiwic3JjL2FwcC9hcHAuY29uZmlnLnRzIiwic3JjL2FwcC9jb3JlL2F1dGgvYXV0aC5ndWFyZC50cyIsInNyYy9hcHAvYXBwLnJvdXRlcy50cyIsInNyYy9hcHAvY29yZS9hcGkvYXV0aC5pbnRlcmNlcHRvci50cyIsInNyYy9hcHAvY29yZS9hcGkvbW9jay10ZWFjaGVycy1hcGkudHMiLCJzcmMvYXBwL2ZlYXR1cmVzL3RlYWNoZXJzL3RlYWNoZXJzLm1vY2sudHMiLCJzcmMvYXBwL2FwcC5jb21wb25lbnQudHMiXSwic291cmNlc0NvbnRlbnQiOlsiaW1wb3J0IHsgYm9vdHN0cmFwQXBwbGljYXRpb24gfSBmcm9tICdAYW5ndWxhci9wbGF0Zm9ybS1icm93c2VyJztcbmltcG9ydCB7IGFwcENvbmZpZyB9IGZyb20gJy4vYXBwL2FwcC5jb25maWcnO1xuaW1wb3J0IHsgQXBwQ29tcG9uZW50IH0gZnJvbSAnLi9hcHAvYXBwLmNvbXBvbmVudCc7XG5cbmJvb3RzdHJhcEFwcGxpY2F0aW9uKEFwcENvbXBvbmVudCwgYXBwQ29uZmlnKS5jYXRjaCgoZXJyKSA9PiBjb25zb2xlLmVycm9yKGVycikpO1xuIiwiaW1wb3J0IHsgQXBwbGljYXRpb25Db25maWcsIExPQ0FMRV9JRCwgcHJvdmlkZVpvbmVDaGFuZ2VEZXRlY3Rpb24gfSBmcm9tICdAYW5ndWxhci9jb3JlJztcbmltcG9ydCB7IHByb3ZpZGVSb3V0ZXIsIHdpdGhDb21wb25lbnRJbnB1dEJpbmRpbmcgfSBmcm9tICdAYW5ndWxhci9yb3V0ZXInO1xuaW1wb3J0IHsgcHJvdmlkZUh0dHBDbGllbnQsIHdpdGhJbnRlcmNlcHRvcnMgfSBmcm9tICdAYW5ndWxhci9jb21tb24vaHR0cCc7XG5pbXBvcnQgeyByZWdpc3RlckxvY2FsZURhdGEgfSBmcm9tICdAYW5ndWxhci9jb21tb24nO1xuaW1wb3J0IGxvY2FsZVpoIGZyb20gJ0Bhbmd1bGFyL2NvbW1vbi9sb2NhbGVzL3poJztcbmltcG9ydCB7IHJvdXRlcyB9IGZyb20gJy4vYXBwLnJvdXRlcyc7XG5pbXBvcnQgeyBhdXRoSW50ZXJjZXB0b3IgfSBmcm9tICcuL2NvcmUvYXBpL2F1dGguaW50ZXJjZXB0b3InO1xuaW1wb3J0IHsgSHR0cFRlYWNoZXJzQXBpU2VydmljZSwgVEVBQ0hFUlNfQVBJIH0gZnJvbSAnLi9jb3JlL2FwaS90ZWFjaGVycy1hcGknO1xuaW1wb3J0IHsgTW9ja1RlYWNoZXJzQXBpU2VydmljZSB9IGZyb20gJy4vY29yZS9hcGkvbW9jay10ZWFjaGVycy1hcGknO1xuXG5yZWdpc3RlckxvY2FsZURhdGEobG9jYWxlWmgpO1xuXG4vKipcbiAqIOaVmeW4iOajgOe0ouaVsOaNrua6kOW8gOWFs++8iFBIQVNFIDTvvInvvJpcbiAqIHRydWUgID0gTW9ja++8iDIwIOWQjeWGhee9ruaVmeW4iO+8jOiBlOiwg+S4jua8lOekuu+8ie+8m1xuICogZmFsc2UgPSBIVFRQ77yIR0VUIC9hcGkvdjEvbWF0Y2hpbmcvdGVhY2hlcnM6c2VhcmNo77yM6ZyA55m75b2V5oCB77yJ44CCXG4gKiDliIfmjaLlj6rmlLnmraTkuIDooYzvvIzpobXpnaLkuI7nu4Tku7bpm7bmlLnliqjjgIJcbiAqL1xuY29uc3QgVVNFX01PQ0tfVEVBQ0hFUlNfQVBJID0gdHJ1ZTtcblxuZXhwb3J0IGNvbnN0IGFwcENvbmZpZzogQXBwbGljYXRpb25Db25maWcgPSB7XG4gIHByb3ZpZGVyczogW1xuICAgIHByb3ZpZGVab25lQ2hhbmdlRGV0ZWN0aW9uKHsgZXZlbnRDb2FsZXNjaW5nOiB0cnVlIH0pLFxuICAgIHByb3ZpZGVSb3V0ZXIocm91dGVzLCB3aXRoQ29tcG9uZW50SW5wdXRCaW5kaW5nKCkpLFxuICAgIHByb3ZpZGVIdHRwQ2xpZW50KHdpdGhJbnRlcmNlcHRvcnMoW2F1dGhJbnRlcmNlcHRvcl0pKSxcbiAgICB7IHByb3ZpZGU6IExPQ0FMRV9JRCwgdXNlVmFsdWU6ICd6aCcgfSxcbiAgICB7XG4gICAgICBwcm92aWRlOiBURUFDSEVSU19BUEksXG4gICAgICB1c2VDbGFzczogVVNFX01PQ0tfVEVBQ0hFUlNfQVBJID8gTW9ja1RlYWNoZXJzQXBpU2VydmljZSA6IEh0dHBUZWFjaGVyc0FwaVNlcnZpY2UsXG4gICAgfSxcbiAgXSxcbn07XG4iLCJpbXBvcnQgeyBpbmplY3QgfSBmcm9tICdAYW5ndWxhci9jb3JlJztcbmltcG9ydCB7IENhbkFjdGl2YXRlRm4sIFJvdXRlciB9IGZyb20gJ0Bhbmd1bGFyL3JvdXRlcic7XG5pbXBvcnQgeyBBdXRoU2VydmljZSB9IGZyb20gJy4vYXV0aC5zZXJ2aWNlJztcbmltcG9ydCB7IFJvbGUgfSBmcm9tICcuL2F1dGgubW9kZWxzJztcblxuLyoqXG4gKiDop5LoibLot6/nlLHlrojljasg4oCUIOS+neaNriBKV1Qg6KeS6Imy5Yet6K+B5L+d5oqk5Zub56uv5o6n5Yi25Y+w44CCXG4gKi9cbmV4cG9ydCBmdW5jdGlvbiByb2xlR3VhcmQocmVxdWlyZWQ6IFJvbGUpOiBDYW5BY3RpdmF0ZUZuIHtcbiAgcmV0dXJuICgpID0+IHtcbiAgICBjb25zdCBhdXRoID0gaW5qZWN0KEF1dGhTZXJ2aWNlKTtcbiAgICBjb25zdCByb3V0ZXIgPSBpbmplY3QoUm91dGVyKTtcbiAgICBpZiAoYXV0aC5pc0xvZ2dlZEluKCkgJiYgYXV0aC5yb2xlKCkgPT09IHJlcXVpcmVkKSB7XG4gICAgICByZXR1cm4gdHJ1ZTtcbiAgICB9XG4gICAgcmV0dXJuIHJvdXRlci5jcmVhdGVVcmxUcmVlKFsnL2F1dGgnXSwgeyBxdWVyeVBhcmFtczogeyByb2xlOiByZXF1aXJlZC50b0xvd2VyQ2FzZSgpIH0gfSk7XG4gIH07XG59XG4iLCJpbXBvcnQgeyBSb3V0ZXMgfSBmcm9tICdAYW5ndWxhci9yb3V0ZXInO1xuaW1wb3J0IHsgUm9sZSB9IGZyb20gJy4vY29yZS9hdXRoL2F1dGgubW9kZWxzJztcbmltcG9ydCB7IHJvbGVHdWFyZCB9IGZyb20gJy4vY29yZS9hdXRoL2F1dGguZ3VhcmQnO1xuXG5leHBvcnQgY29uc3Qgcm91dGVzOiBSb3V0ZXMgPSBbXG4gIHsgcGF0aDogJycsIGxvYWRDb21wb25lbnQ6ICgpID0+IGltcG9ydCgnLi9mZWF0dXJlcy9sYW5kaW5nL2xhbmRpbmcuY29tcG9uZW50JykudGhlbigobSkgPT4gbS5MYW5kaW5nQ29tcG9uZW50KSB9LFxuICB7IHBhdGg6ICd0ZWFjaGVycycsIGxvYWRDb21wb25lbnQ6ICgpID0+IGltcG9ydCgnLi9mZWF0dXJlcy90ZWFjaGVycy90ZWFjaGVycy5jb21wb25lbnQnKS50aGVuKChtKSA9PiBtLlRlYWNoZXJzQ29tcG9uZW50KSB9LFxuICB7IHBhdGg6ICdhdXRoJywgbG9hZENvbXBvbmVudDogKCkgPT4gaW1wb3J0KCcuL2ZlYXR1cmVzL2F1dGgvYXV0aC1wb3J0YWwuY29tcG9uZW50JykudGhlbigobSkgPT4gbS5BdXRoUG9ydGFsQ29tcG9uZW50KSB9LFxuICB7XG4gICAgcGF0aDogJ3N0dWRlbnQnLFxuICAgIGNhbkFjdGl2YXRlOiBbcm9sZUd1YXJkKFJvbGUuU1RVREVOVCldLFxuICAgIGNoaWxkcmVuOiBbXG4gICAgICB7IHBhdGg6ICcnLCByZWRpcmVjdFRvOiAnZGFzaGJvYXJkJywgcGF0aE1hdGNoOiAnZnVsbCcgfSxcbiAgICAgIHsgcGF0aDogJ2Rhc2hib2FyZCcsIGxvYWRDb21wb25lbnQ6ICgpID0+IGltcG9ydCgnLi9mZWF0dXJlcy9zdHVkZW50L3N0dWRlbnQtZGFzaGJvYXJkLmNvbXBvbmVudCcpLnRoZW4oKG0pID0+IG0uU3R1ZGVudERhc2hib2FyZENvbXBvbmVudCkgfSxcbiAgICAgIHsgcGF0aDogJ21hdGNoJywgbG9hZENvbXBvbmVudDogKCkgPT4gaW1wb3J0KCcuL2ZlYXR1cmVzL21hdGNoL21hdGNoLmNvbXBvbmVudCcpLnRoZW4oKG0pID0+IG0uTWF0Y2hDb21wb25lbnQpIH0sXG4gICAgXSxcbiAgfSxcbiAge1xuICAgIHBhdGg6ICdwYXJlbnQnLFxuICAgIGNhbkFjdGl2YXRlOiBbcm9sZUd1YXJkKFJvbGUuUEFSRU5UKV0sXG4gICAgY2hpbGRyZW46IFtcbiAgICAgIHsgcGF0aDogJycsIHJlZGlyZWN0VG86ICdkYXNoYm9hcmQnLCBwYXRoTWF0Y2g6ICdmdWxsJyB9LFxuICAgICAgeyBwYXRoOiAnZGFzaGJvYXJkJywgbG9hZENvbXBvbmVudDogKCkgPT4gaW1wb3J0KCcuL2ZlYXR1cmVzL3BhcmVudC9wYXJlbnQtZGFzaGJvYXJkLmNvbXBvbmVudCcpLnRoZW4oKG0pID0+IG0uUGFyZW50RGFzaGJvYXJkQ29tcG9uZW50KSB9LFxuICAgIF0sXG4gIH0sXG4gIHtcbiAgICBwYXRoOiAndGVhY2hlcicsXG4gICAgY2FuQWN0aXZhdGU6IFtyb2xlR3VhcmQoUm9sZS5URUFDSEVSKV0sXG4gICAgY2hpbGRyZW46IFtcbiAgICAgIHsgcGF0aDogJycsIHJlZGlyZWN0VG86ICdkYXNoYm9hcmQnLCBwYXRoTWF0Y2g6ICdmdWxsJyB9LFxuICAgICAgeyBwYXRoOiAnZGFzaGJvYXJkJywgbG9hZENvbXBvbmVudDogKCkgPT4gaW1wb3J0KCcuL2ZlYXR1cmVzL3RlYWNoZXIvdGVhY2hlci1kYXNoYm9hcmQuY29tcG9uZW50JykudGhlbigobSkgPT4gbS5UZWFjaGVyRGFzaGJvYXJkQ29tcG9uZW50KSB9LFxuICAgIF0sXG4gIH0sXG4gIHtcbiAgICBwYXRoOiAnYWRtaW4nLFxuICAgIGNhbkFjdGl2YXRlOiBbcm9sZUd1YXJkKFJvbGUuQURNSU4pXSxcbiAgICBjaGlsZHJlbjogW1xuICAgICAgeyBwYXRoOiAnJywgcmVkaXJlY3RUbzogJ2Rhc2hib2FyZCcsIHBhdGhNYXRjaDogJ2Z1bGwnIH0sXG4gICAgICB7IHBhdGg6ICdkYXNoYm9hcmQnLCBsb2FkQ29tcG9uZW50OiAoKSA9PiBpbXBvcnQoJy4vZmVhdHVyZXMvYWRtaW4vYWRtaW4tZGFzaGJvYXJkLmNvbXBvbmVudCcpLnRoZW4oKG0pID0+IG0uQWRtaW5EYXNoYm9hcmRDb21wb25lbnQpIH0sXG4gICAgXSxcbiAgfSxcbiAgeyBwYXRoOiAnKionLCByZWRpcmVjdFRvOiAnJyB9LFxuXTtcbiIsImltcG9ydCB7IEh0dHBJbnRlcmNlcHRvckZuIH0gZnJvbSAnQGFuZ3VsYXIvY29tbW9uL2h0dHAnO1xuaW1wb3J0IHsgSW5qZWN0YWJsZSwgaW5qZWN0IH0gZnJvbSAnQGFuZ3VsYXIvY29yZSc7XG5pbXBvcnQgeyBIdHRwSGFuZGxlckZuLCBIdHRwUmVxdWVzdCwgSHR0cEV2ZW50IH0gZnJvbSAnQGFuZ3VsYXIvY29tbW9uL2h0dHAnO1xuaW1wb3J0IHsgT2JzZXJ2YWJsZSB9IGZyb20gJ3J4anMnO1xuaW1wb3J0IHsgQXV0aFNlcnZpY2UgfSBmcm9tICcuLi9hdXRoL2F1dGguc2VydmljZSc7XG5cbi8qKlxuICog6Ym05p2D5oum5oiq5ZmoIOKAlCDpmYTliqAgSldUIEJlYXJlciDlh63or4HkuI7lhpnmk43kvZzluYLnrYnplK7jgIJcbiAqL1xuZXhwb3J0IGNvbnN0IGF1dGhJbnRlcmNlcHRvcjogSHR0cEludGVyY2VwdG9yRm4gPSAoXG4gIHJlcTogSHR0cFJlcXVlc3Q8dW5rbm93bj4sXG4gIG5leHQ6IEh0dHBIYW5kbGVyRm4sXG4pOiBPYnNlcnZhYmxlPEh0dHBFdmVudDx1bmtub3duPj4gPT4ge1xuICBjb25zdCBhdXRoID0gaW5qZWN0KEF1dGhTZXJ2aWNlKTtcbiAgY29uc3Qgc2Vzc2lvbiA9IGF1dGguc2Vzc2lvbigpO1xuXG4gIGxldCByZXF1ZXN0ID0gcmVxO1xuICBpZiAoc2Vzc2lvbikge1xuICAgIHJlcXVlc3QgPSByZXF1ZXN0LmNsb25lKHsgc2V0SGVhZGVyczogeyBBdXRob3JpemF0aW9uOiBgQmVhcmVyICR7c2Vzc2lvbi5hY2Nlc3NUb2tlbn1gIH0gfSk7XG4gIH1cbiAgaWYgKFsnUE9TVCcsICdQVVQnLCAnUEFUQ0gnLCAnREVMRVRFJ10uaW5jbHVkZXMocmVxdWVzdC5tZXRob2QpKSB7XG4gICAgLy8g5by65bmC562J5oCn5L+d6Zqc77ya5omA5pyJ5pyJ54q25oCB5YaZ5pON5L2c5b+F6aG75pC65bimIElkZW1wb3RlbmN5LUtleSAoVVVJRHY0KVxuICAgIHJlcXVlc3QgPSByZXF1ZXN0LmNsb25lKHsgc2V0SGVhZGVyczogeyAnSWRlbXBvdGVuY3ktS2V5JzogY3J5cHRvLnJhbmRvbVVVSUQoKSB9IH0pO1xuICB9XG4gIHJldHVybiBuZXh0KHJlcXVlc3QpO1xufTtcbiIsImltcG9ydCB7IEluamVjdGFibGUgfSBmcm9tICdAYW5ndWxhci9jb3JlJztcbmltcG9ydCB7IE9ic2VydmFibGUsIG9mIH0gZnJvbSAncnhqcyc7XG5pbXBvcnQgeyBkZWxheSB9IGZyb20gJ3J4anMvb3BlcmF0b3JzJztcbmltcG9ydCB7IFRlYWNoZXJzQXBpLCBUZWFjaGVyUXVlcnkgfSBmcm9tICcuL3RlYWNoZXJzLWFwaSc7XG5pbXBvcnQgeyBUZWFjaGVyU3VtbWFyeSB9IGZyb20gJy4uLy4uL3NoYXJlZC9tb2RlbHMvdGVhY2hlci5tb2RlbCc7XG5pbXBvcnQgeyBBTExfVEVBQ0hFUlMgfSBmcm9tICcuLi8uLi9mZWF0dXJlcy90ZWFjaGVycy90ZWFjaGVycy5tb2NrJztcblxuLyoqXG4gKiDmlZnluIjmo4DntKIgTW9jayDlrp7njrAg4oCUIOaVsOaNrua6kOS4uiAyMCDlkI3lhoXnva7mlZnluIjvvIjop4TojIPkuozljYHkuZ3vvInjgIJcbiAqIOetm+mAieS4juaOkuW6j+ivreS5iemAkOadoemVnOWDj+WQjuerryBNYXRjaGluZ0FwcGxpY2F0aW9uU2VydmljZS5zZWFyY2jvvJpcbiAqIOiupOivgem7mOiupOi/h+a7pOOAgeS7t+agvOS4iumZkOOAgeWtpuauteeyvuehruOAgemjjuagvOagh+etvueyvuehruOAgeWFs+mUruivjeWMheWQq+WMuemFje+8m1xuICog5o6S5bqP5Li66YCP5piO6K+B5o2u5bqP77yI6K6k6K+BID4g6K+E5Lu36K+B5o2uID4g5Y+v57qm5pe26Ze077yJ77yM5LiN6Jma5p6E5Yy56YWN55m+5YiG5q+U44CCXG4gKiAyNTBtcyDmqKHmi5/ml7blu7bnlKjkuo7mvJTnu4PkuI4gSFRUUCDkuIDoh7TnmoTliqDovb3mgIHjgIJcbiAqL1xuQEluamVjdGFibGUoeyBwcm92aWRlZEluOiAncm9vdCcgfSlcbmV4cG9ydCBjbGFzcyBNb2NrVGVhY2hlcnNBcGlTZXJ2aWNlIGV4dGVuZHMgVGVhY2hlcnNBcGkge1xuXG4gIG92ZXJyaWRlIHNlYXJjaChxdWVyeTogVGVhY2hlclF1ZXJ5KTogT2JzZXJ2YWJsZTxUZWFjaGVyU3VtbWFyeVtdPiB7XG4gICAgY29uc3Qga2V5d29yZCA9IHF1ZXJ5LmtleXdvcmQ/LnRyaW0oKTtcbiAgICBjb25zdCBsaXN0ID0gQUxMX1RFQUNIRVJTLmZpbHRlcigodCkgPT4ge1xuICAgICAgaWYgKCFxdWVyeS5pbmNsdWRlVW52ZXJpZmllZCAmJiAhdC52ZXJpZmljYXRpb25BcHByb3ZlZCkge1xuICAgICAgICByZXR1cm4gZmFsc2U7XG4gICAgICB9XG4gICAgICBpZiAocXVlcnkuc3ViamVjdCAmJiAhdC5zdWJqZWN0LmluY2x1ZGVzKHF1ZXJ5LnN1YmplY3QpKSB7XG4gICAgICAgIHJldHVybiBmYWxzZTtcbiAgICAgIH1cbiAgICAgIGlmIChxdWVyeS5ncmFkZUJhbmQgJiYgdC5ncmFkZUJhbmQgIT09IHF1ZXJ5LmdyYWRlQmFuZCkge1xuICAgICAgICByZXR1cm4gZmFsc2U7XG4gICAgICB9XG4gICAgICBpZiAocXVlcnkubWF4UHJpY2VQZXJIb3VyQ2VudHMgIT0gbnVsbCAmJiB0LnByaWNlUGVySG91ckNlbnRzID4gcXVlcnkubWF4UHJpY2VQZXJIb3VyQ2VudHMpIHtcbiAgICAgICAgcmV0dXJuIGZhbHNlO1xuICAgICAgfVxuICAgICAgaWYgKHF1ZXJ5LnN0eWxlVGFnICYmICF0LnN0eWxlVGFncy5pbmNsdWRlcyhxdWVyeS5zdHlsZVRhZykpIHtcbiAgICAgICAgcmV0dXJuIGZhbHNlO1xuICAgICAgfVxuICAgICAgaWYgKGtleXdvcmQpIHtcbiAgICAgICAgY29uc3QgaGF5c3RhY2sgPSBgJHt0LnN1YmplY3R9ICR7dC5ncmFkZUJhbmR9ICR7dC5zY2hvb2x9ICR7dC5kaXNwbGF5TmFtZX1gO1xuICAgICAgICBpZiAoIWhheXN0YWNrLmluY2x1ZGVzKGtleXdvcmQpKSB7XG4gICAgICAgICAgcmV0dXJuIGZhbHNlO1xuICAgICAgICB9XG4gICAgICB9XG4gICAgICByZXR1cm4gdHJ1ZTtcbiAgICB9KTtcblxuICAgIGNvbnN0IHNvcnRlZCA9IFsuLi5saXN0XS5zb3J0KChhLCBiKSA9PiBldmlkZW5jZVNjb3JlKGIpIC0gZXZpZGVuY2VTY29yZShhKSk7XG4gICAgLy8g5qih5ouf572R57uc5pe25bu277yM6amx5Yqo6aG16Z2i55qE5Yqg6L296aqo5p625oCBXG4gICAgcmV0dXJuIG9mKHNvcnRlZCkucGlwZShkZWxheSgyNTApKTtcbiAgfVxufVxuXG4vKiog5LiO5ZCO56uvIGV2aWRlbmNlU2NvcmUg5a+56b2Q77ya6K6k6K+BIDIg5YiGICsg5pyJ6K+E5Lu3IDEg5YiGICsg5pyJ5Y+v57qm5qGj5pyfIDEg5YiG44CCICovXG5mdW5jdGlvbiBldmlkZW5jZVNjb3JlKHQ6IFRlYWNoZXJTdW1tYXJ5KTogbnVtYmVyIHtcbiAgbGV0IHNjb3JlID0gMDtcbiAgaWYgKHQudmVyaWZpY2F0aW9uQXBwcm92ZWQpIHNjb3JlICs9IDI7XG4gIGlmICh0LnJldmlld0NvdW50ID4gMCkgc2NvcmUgKz0gMTtcbiAgaWYgKHQubmV4dE9wZW5TbG90QXQpIHNjb3JlICs9IDE7XG4gIHJldHVybiBzY29yZTtcbn1cbiIsImltcG9ydCB7IFRlYWNoZXJTdW1tYXJ5IH0gZnJvbSAnLi4vLi4vc2hhcmVkL21vZGVscy90ZWFjaGVyLm1vZGVsJztcbmltcG9ydCB7IEZFQVRVUkVEX1RFQUNIRVJTIH0gZnJvbSAnLi4vbGFuZGluZy9sYW5kaW5nLWRhdGEnO1xuXG4vKipcbiAqIOaVmeW4iOWIl+ihqOmhtSBNb2NrIOaVsOaNru+8iOinhOiMg+S6jOWNgeS5ne+8mjIwIOWQjeaVmeW4iO+8ie+8mlxuICog5YmNIDYg5ZCN5LiO6aaW6aG144CM57K+6YCJ5a+85biI44CN5ZCM5rqQ77yIRkVBVFVSRURfVEVBQ0hFUlPvvInvvIzlkI4gMTQg5ZCN6KGl5YWo5a2m56eRL+WtpuautS/ku7fkvY3liIbluIPjgIJcbiAqIOe7k+aehOS4jiBHRVQgL2FwaS92MS9tYXRjaGluZy90ZWFjaGVyczpzZWFyY2gg6IGa5ZCI6KeG5Zu+6YCQ5a2X5q615LiA6Ie0XG4gKiDvvIhtYXRjaGluZy5UZWFjaGVyQ2FuZGlkYXRlIMOXIHRlYWNoZXIuUHJvZmlsZVJlc3BvbnNl77yMY2FtZWxDYXNl77yM5Lu35qC85Y2V5L2N5Li65YiG77yJ44CCXG4gKi9cbmV4cG9ydCBjb25zdCBBTExfVEVBQ0hFUlM6IFRlYWNoZXJTdW1tYXJ5W10gPSBbXG4gIC4uLkZFQVRVUkVEX1RFQUNIRVJTLFxuICB7XG4gICAgdGVhY2hlcklkOiAnMDE5MzBlZWUtNmEwMS03YjJlLWIxYzAtMWEyYjNjNGQ1ZTA3JyxcbiAgICBkaXNwbGF5TmFtZTogJ+ayiOingua+nCcsXG4gICAgc2Nob29sOiAn5YyX5Lqs5aSn5a2mJyxcbiAgICBoaWdoZXN0RGVncmVlOiAn5Y2a5aOrJyxcbiAgICB5ZWFyc09mVGVhY2hpbmc6IDExLFxuICAgIHN1YmplY3Q6ICfmlbDlraYnLFxuICAgIGdyYWRlQmFuZDogJ+mrmOS4rScsXG4gICAgcHJpY2VQZXJIb3VyQ2VudHM6IDM5ODAwLFxuICAgIHZlcmlmaWNhdGlvbkFwcHJvdmVkOiB0cnVlLFxuICAgIGF2ZXJhZ2VSYXRpbmc6IDQuOSxcbiAgICByZXZpZXdDb3VudDogMjAxLFxuICAgIG5leHRPcGVuU2xvdEF0OiAnMjAyNi0xMC0wMlQxOTowMDowMCswODowMCcsXG4gICAgc3R5bGVUYWdzOiBbJ+aAnee7tOaOqOWvvCcsICfnq57otZvovoXlr7wnLCAn5rex5YWl5rWF5Ye6J10sXG4gIH0sXG4gIHtcbiAgICB0ZWFjaGVySWQ6ICcwMTkzMGVlZS02YTAxLTdiMmUtYjFjMC0xYTJiM2M0ZDVlMDgnLFxuICAgIGRpc3BsYXlOYW1lOiAn6aG+5riF56a+JyxcbiAgICBzY2hvb2w6ICflpI3ml6blpKflraYnLFxuICAgIGhpZ2hlc3REZWdyZWU6ICfnoZXlo6snLFxuICAgIHllYXJzT2ZUZWFjaGluZzogNSxcbiAgICBzdWJqZWN0OiAn6Iux6K+tJyxcbiAgICBncmFkZUJhbmQ6ICfliJ3kuK0nLFxuICAgIHByaWNlUGVySG91ckNlbnRzOiAxODgwMCxcbiAgICB2ZXJpZmljYXRpb25BcHByb3ZlZDogdHJ1ZSxcbiAgICBhdmVyYWdlUmF0aW5nOiA0LjgsXG4gICAgcmV2aWV3Q291bnQ6IDE0MyxcbiAgICBuZXh0T3BlblNsb3RBdDogJzIwMjYtMDktMjhUMTk6MDA6MDArMDg6MDAnLFxuICAgIHN0eWxlVGFnczogWyfmg4Xmma/lr7nor50nLCAn5YW06Laj5byV5a+8JywgJ+iAkOW/g+e7huiHtCddLFxuICB9LFxuICB7XG4gICAgdGVhY2hlcklkOiAnMDE5MzBlZWUtNmEwMS03YjJlLWIxYzAtMWEyYjNjNGQ1ZTA5JyxcbiAgICBkaXNwbGF5TmFtZTogJ+mZhumXu+esmScsXG4gICAgc2Nob29sOiAn5LiK5rW35Lqk6YCa5aSn5a2mJyxcbiAgICBoaWdoZXN0RGVncmVlOiAn56GV5aOrJyxcbiAgICB5ZWFyc09mVGVhY2hpbmc6IDQsXG4gICAgc3ViamVjdDogJ+eJqeeQhicsXG4gICAgZ3JhZGVCYW5kOiAn5Yid5LitJyxcbiAgICBwcmljZVBlckhvdXJDZW50czogMjA4MDAsXG4gICAgdmVyaWZpY2F0aW9uQXBwcm92ZWQ6IHRydWUsXG4gICAgYXZlcmFnZVJhdGluZzogNC43LFxuICAgIHJldmlld0NvdW50OiA3NixcbiAgICBuZXh0T3BlblNsb3RBdDogJzIwMjYtMDktMjlUMTg6MzA6MDArMDg6MDAnLFxuICAgIHN0eWxlVGFnczogWyfmqKHlnovmnoTlu7onLCAn5rex5YWl5rWF5Ye6JywgJ+WQr+WPkeW8j+aPkOmXriddLFxuICB9LFxuICB7XG4gICAgdGVhY2hlcklkOiAnMDE5MzBlZWUtNmEwMS03YjJlLWIxYzAtMWEyYjNjNGQ1ZTBhJyxcbiAgICBkaXNwbGF5TmFtZTogJ+iLj+egmumdkicsXG4gICAgc2Nob29sOiAn5Y2X5Lqs5aSn5a2mJyxcbiAgICBoaWdoZXN0RGVncmVlOiAn5a2m5aOrJyxcbiAgICB5ZWFyc09mVGVhY2hpbmc6IDMsXG4gICAgc3ViamVjdDogJ+WMluWtpicsXG4gICAgZ3JhZGVCYW5kOiAn5Yid5LitJyxcbiAgICBwcmljZVBlckhvdXJDZW50czogMTY4MDAsXG4gICAgdmVyaWZpY2F0aW9uQXBwcm92ZWQ6IHRydWUsXG4gICAgYXZlcmFnZVJhdGluZzogNC42LFxuICAgIHJldmlld0NvdW50OiA1OCxcbiAgICBuZXh0T3BlblNsb3RBdDogJzIwMjYtMTAtMDFUMjA6MDA6MDArMDg6MDAnLFxuICAgIHN0eWxlVGFnczogWyflrp7pqozmjqLnqbYnLCAn56iz5omO56iz5omTJywgJ+iAkOW/g+e7huiHtCddLFxuICB9LFxuICB7XG4gICAgdGVhY2hlcklkOiAnMDE5MzBlZWUtNmEwMS03YjJlLWIxYzAtMWEyYjNjNGQ1ZTBiJyxcbiAgICBkaXNwbGF5TmFtZTogJ+eoi+mbqOecoCcsXG4gICAgc2Nob29sOiAn5rWZ5rGf5aSn5a2mJyxcbiAgICBoaWdoZXN0RGVncmVlOiAn56GV5aOrJyxcbiAgICB5ZWFyc09mVGVhY2hpbmc6IDYsXG4gICAgc3ViamVjdDogJ+eUn+eJqScsXG4gICAgZ3JhZGVCYW5kOiAn6auY5LitJyxcbiAgICBwcmljZVBlckhvdXJDZW50czogMjU4MDAsXG4gICAgdmVyaWZpY2F0aW9uQXBwcm92ZWQ6IHRydWUsXG4gICAgYXZlcmFnZVJhdGluZzogNC44LFxuICAgIHJldmlld0NvdW50OiA5MSxcbiAgICBuZXh0T3BlblNsb3RBdDogJzIwMjYtMDktMzBUMTg6MDA6MDArMDg6MDAnLFxuICAgIHN0eWxlVGFnczogWyfnn6Xor4bnvZHnu5wnLCAn5Zu+6Kej6K6w5b+GJywgJ+eos+aJjueos+aJkyddLFxuICB9LFxuICB7XG4gICAgdGVhY2hlcklkOiAnMDE5MzBlZWUtNmEwMS03YjJlLWIxYzAtMWEyYjNjNGQ1ZTBjJyxcbiAgICBkaXNwbGF5TmFtZTogJ+ael+i/sOaAgCcsXG4gICAgc2Nob29sOiAn5q2m5rGJ5aSn5a2mJyxcbiAgICBoaWdoZXN0RGVncmVlOiAn56GV5aOrJyxcbiAgICB5ZWFyc09mVGVhY2hpbmc6IDgsXG4gICAgc3ViamVjdDogJ+WOhuWPsicsXG4gICAgZ3JhZGVCYW5kOiAn6auY5LitJyxcbiAgICBwcmljZVBlckhvdXJDZW50czogMjE4MDAsXG4gICAgdmVyaWZpY2F0aW9uQXBwcm92ZWQ6IHRydWUsXG4gICAgYXZlcmFnZVJhdGluZzogNC45LFxuICAgIHJldmlld0NvdW50OiAxMTIsXG4gICAgbmV4dE9wZW5TbG90QXQ6ICcyMDI2LTA5LTI4VDIwOjAwOjAwKzA4OjAwJyxcbiAgICBzdHlsZVRhZ3M6IFsn5paH5Y+y6LSv6YCaJywgJ+aVheS6i+WMluaVmeWtpicsICfmlofmnKznu4bor7snXSxcbiAgfSxcbiAge1xuICAgIHRlYWNoZXJJZDogJzAxOTMwZWVlLTZhMDEtN2IyZS1iMWMwLTFhMmIzYzRkNWUwZCcsXG4gICAgZGlzcGxheU5hbWU6ICfmsojnlo/moZAnLFxuICAgIHNjaG9vbDogJ+S4reWxseWkp+WtpicsXG4gICAgaGlnaGVzdERlZ3JlZTogJ+WtpuWjqycsXG4gICAgeWVhcnNPZlRlYWNoaW5nOiA0LFxuICAgIHN1YmplY3Q6ICflnLDnkIYnLFxuICAgIGdyYWRlQmFuZDogJ+WIneS4rScsXG4gICAgcHJpY2VQZXJIb3VyQ2VudHM6IDE1ODAwLFxuICAgIHZlcmlmaWNhdGlvbkFwcHJvdmVkOiB0cnVlLFxuICAgIGF2ZXJhZ2VSYXRpbmc6IDQuNSxcbiAgICByZXZpZXdDb3VudDogNDQsXG4gICAgbmV4dE9wZW5TbG90QXQ6ICcyMDI2LTEwLTAzVDE5OjAwOjAwKzA4OjAwJyxcbiAgICBzdHlsZVRhZ3M6IFsn5Zyw5Zu+5oCd57u0JywgJ+ahiOS+i+aVmeWtpicsICfnqLPmiY7nqLPmiZMnXSxcbiAgfSxcbiAge1xuICAgIHRlYWNoZXJJZDogJzAxOTMwZWVlLTZhMDEtN2IyZS1iMWMwLTFhMmIzYzRkNWUwZScsXG4gICAgZGlzcGxheU5hbWU6ICfpob7ljJfovrAnLFxuICAgIHNjaG9vbDogJ+WTiOWwlOa7qOW3peS4muWkp+WtpicsXG4gICAgaGlnaGVzdERlZ3JlZTogJ+ehleWjqycsXG4gICAgeWVhcnNPZlRlYWNoaW5nOiA3LFxuICAgIHN1YmplY3Q6ICfkv6Hmga/lraYnLFxuICAgIGdyYWRlQmFuZDogJ+mrmOS4rScsXG4gICAgcHJpY2VQZXJIb3VyQ2VudHM6IDM1ODAwLFxuICAgIHZlcmlmaWNhdGlvbkFwcHJvdmVkOiB0cnVlLFxuICAgIGF2ZXJhZ2VSYXRpbmc6IDQuOSxcbiAgICByZXZpZXdDb3VudDogMTY4LFxuICAgIG5leHRPcGVuU2xvdEF0OiAnMjAyNi0wOS0yOVQxOTozMDowMCswODowMCcsXG4gICAgc3R5bGVUYWdzOiBbJ+eul+azleaAnee7tCcsICfpobnnm67lrp7miJgnLCAn56ue6LWb6L6F5a+8J10sXG4gIH0sXG4gIHtcbiAgICB0ZWFjaGVySWQ6ICcwMTkzMGVlZS02YTAxLTdiMmUtYjFjMC0xYTJiM2M0ZDVlMGYnLFxuICAgIGRpc3BsYXlOYW1lOiAn6ZmG6JST6Z+zJyxcbiAgICBzY2hvb2w6ICfljJfkuqzluIjojIPlpKflraYnLFxuICAgIGhpZ2hlc3REZWdyZWU6ICfnoZXlo6snLFxuICAgIHllYXJzT2ZUZWFjaGluZzogNixcbiAgICBzdWJqZWN0OiAn6K+t5paHJyxcbiAgICBncmFkZUJhbmQ6ICfliJ3kuK0nLFxuICAgIHByaWNlUGVySG91ckNlbnRzOiAxNzgwMCxcbiAgICB2ZXJpZmljYXRpb25BcHByb3ZlZDogdHJ1ZSxcbiAgICBhdmVyYWdlUmF0aW5nOiA0LjgsXG4gICAgcmV2aWV3Q291bnQ6IDg3LFxuICAgIG5leHRPcGVuU2xvdEF0OiAnMjAyNi0wOS0zMFQxOTowMDowMCswODowMCcsXG4gICAgc3R5bGVUYWdzOiBbJ+aWh+acrOe7huivuycsICflhpnkvZzmgJ3nu7QnLCAn6Zmq5Ly05byP552j5a+8J10sXG4gIH0sXG4gIHtcbiAgICB0ZWFjaGVySWQ6ICcwMTkzMGVlZS02YTAxLTdiMmUtYjFjMC0xYTJiM2M0ZDVlMTAnLFxuICAgIGRpc3BsYXlOYW1lOiAn6IuP6KGM6IifJyxcbiAgICBzY2hvb2w6ICfljZflvIDlpKflraYnLFxuICAgIGhpZ2hlc3REZWdyZWU6ICflrablo6snLFxuICAgIHllYXJzT2ZUZWFjaGluZzogMyxcbiAgICBzdWJqZWN0OiAn5pWw5a2mJyxcbiAgICBncmFkZUJhbmQ6ICfliJ3kuK0nLFxuICAgIHByaWNlUGVySG91ckNlbnRzOiAxNDgwMCxcbiAgICB2ZXJpZmljYXRpb25BcHByb3ZlZDogdHJ1ZSxcbiAgICBhdmVyYWdlUmF0aW5nOiA0LjQsXG4gICAgcmV2aWV3Q291bnQ6IDM5LFxuICAgIG5leHRPcGVuU2xvdEF0OiAnMjAyNi0wOS0yOFQxODowMDowMCswODowMCcsXG4gICAgc3R5bGVUYWdzOiBbJ+iAg+eCueaLhuinoycsICfplJnpopjnrqHnkIYnLCAn56iz5omO56iz5omTJ10sXG4gIH0sXG4gIHtcbiAgICB0ZWFjaGVySWQ6ICcwMTkzMGVlZS02YTAxLTdiMmUtYjFjMC0xYTJiM2M0ZDVlMTEnLFxuICAgIGRpc3BsYXlOYW1lOiAn56iL55+l6K64JyxcbiAgICBzY2hvb2w6ICflm5vlt53lpKflraYnLFxuICAgIGhpZ2hlc3REZWdyZWU6ICfnoZXlo6snLFxuICAgIHllYXJzT2ZUZWFjaGluZzogNSxcbiAgICBzdWJqZWN0OiAn6Iux6K+tJyxcbiAgICBncmFkZUJhbmQ6ICflsI/lraYnLFxuICAgIHByaWNlUGVySG91ckNlbnRzOiAxMjgwMCxcbiAgICB2ZXJpZmljYXRpb25BcHByb3ZlZDogdHJ1ZSxcbiAgICBhdmVyYWdlUmF0aW5nOiA0LjcsXG4gICAgcmV2aWV3Q291bnQ6IDY2LFxuICAgIG5leHRPcGVuU2xvdEF0OiAnMjAyNi0xMC0wMVQxODozMDowMCswODowMCcsXG4gICAgc3R5bGVUYWdzOiBbJ+iHqueEtuaLvOivuycsICfmuLjmiI/ljJbmlZnlraYnLCAn5YW06Laj5byV5a+8J10sXG4gIH0sXG4gIHtcbiAgICB0ZWFjaGVySWQ6ICcwMTkzMGVlZS02YTAxLTdiMmUtYjFjMC0xYTJiM2M0ZDVlMTInLFxuICAgIGRpc3BsYXlOYW1lOiAn5p6X5pyb6IiSJyxcbiAgICBzY2hvb2w6ICfljY7kuJzluIjojIPlpKflraYnLFxuICAgIGhpZ2hlc3REZWdyZWU6ICfnoZXlo6snLFxuICAgIHllYXJzT2ZUZWFjaGluZzogMixcbiAgICBzdWJqZWN0OiAn5pWw5a2mJyxcbiAgICBncmFkZUJhbmQ6ICflsI/lraYnLFxuICAgIHByaWNlUGVySG91ckNlbnRzOiA5ODAwLFxuICAgIHZlcmlmaWNhdGlvbkFwcHJvdmVkOiB0cnVlLFxuICAgIGF2ZXJhZ2VSYXRpbmc6IG51bGwsXG4gICAgcmV2aWV3Q291bnQ6IDAsXG4gICAgbmV4dE9wZW5TbG90QXQ6ICcyMDI2LTA5LTI5VDE3OjAwOjAwKzA4OjAwJyxcbiAgICBzdHlsZVRhZ3M6IFsn5ri45oiP5YyW5pWZ5a2mJywgJ+iAkOW/g+e7huiHtCcsICfmt7HlhaXmtYXlh7onXSxcbiAgfSxcbiAge1xuICAgIHRlYWNoZXJJZDogJzAxOTMwZWVlLTZhMDEtN2IyZS1iMWMwLTFhMmIzYzRkNWUxMycsXG4gICAgZGlzcGxheU5hbWU6ICfmsojnoJrovp4nLFxuICAgIHNjaG9vbDogJ+WQjOa1juWkp+WtpicsXG4gICAgaGlnaGVzdERlZ3JlZTogJ+WtpuWjqycsXG4gICAgeWVhcnNPZlRlYWNoaW5nOiAyLFxuICAgIHN1YmplY3Q6ICfniannkIYnLFxuICAgIGdyYWRlQmFuZDogJ+mrmOS4rScsXG4gICAgcHJpY2VQZXJIb3VyQ2VudHM6IDIzODAwLFxuICAgIHZlcmlmaWNhdGlvbkFwcHJvdmVkOiBmYWxzZSxcbiAgICBhdmVyYWdlUmF0aW5nOiBudWxsLFxuICAgIHJldmlld0NvdW50OiAwLFxuICAgIG5leHRPcGVuU2xvdEF0OiAnMjAyNi0xMC0wMlQxODowMDowMCswODowMCcsXG4gICAgc3R5bGVUYWdzOiBbJ+iAg+eCueaLhuinoycsICfmgJ3nu7Tmjqjlr7wnXSxcbiAgfSxcbiAge1xuICAgIHRlYWNoZXJJZDogJzAxOTMwZWVlLTZhMDEtN2IyZS1iMWMwLTFhMmIzYzRkNWUxNCcsXG4gICAgZGlzcGxheU5hbWU6ICfpob7pnZnlp50nLFxuICAgIHNjaG9vbDogJ+WMl+S6rOWkluWbveivreWkp+WtpicsXG4gICAgaGlnaGVzdERlZ3JlZTogJ+ehleWjqycsXG4gICAgeWVhcnNPZlRlYWNoaW5nOiA0LFxuICAgIHN1YmplY3Q6ICfoi7Hor60nLFxuICAgIGdyYWRlQmFuZDogJ+mrmOS4rScsXG4gICAgcHJpY2VQZXJIb3VyQ2VudHM6IDI4ODAwLFxuICAgIHZlcmlmaWNhdGlvbkFwcHJvdmVkOiBmYWxzZSxcbiAgICBhdmVyYWdlUmF0aW5nOiBudWxsLFxuICAgIHJldmlld0NvdW50OiAwLFxuICAgIG5leHRPcGVuU2xvdEF0OiBudWxsLFxuICAgIHN0eWxlVGFnczogWyfmg4Xmma/lr7nor50nLCAn5YaZ5L2c5oCd57u0J10sXG4gIH0sXG5dO1xuIiwiaW1wb3J0IHsgQ29tcG9uZW50IH0gZnJvbSAnQGFuZ3VsYXIvY29yZSc7XG5pbXBvcnQgeyBSb3V0ZXJPdXRsZXQgfSBmcm9tICdAYW5ndWxhci9yb3V0ZXInO1xuXG5AQ29tcG9uZW50KHtcbiAgc2VsZWN0b3I6ICdudXItcm9vdCcsXG4gIHN0YW5kYWxvbmU6IHRydWUsXG4gIGltcG9ydHM6IFtSb3V0ZXJPdXRsZXRdLFxuICB0ZW1wbGF0ZTogYFxuICAgIDxhIGNsYXNzPVwic2tpcC1saW5rXCIgaHJlZj1cIiNtYWluLWNvbnRlbnRcIj7ot7PovazliLDkuLvopoHlhoXlrrk8L2E+XG4gICAgPG1haW4gaWQ9XCJtYWluLWNvbnRlbnRcIj5cbiAgICAgIDxyb3V0ZXItb3V0bGV0IC8+XG4gICAgPC9tYWluPlxuICBgLFxufSlcbmV4cG9ydCBjbGFzcyBBcHBDb21wb25lbnQge31cbiJdLCJtYXBwaW5ncyI6Ijs7Ozs7Ozs7Ozs7Ozs7OztBQUFBLFNBQVMsNEJBQTRCOzs7QUNBckMsU0FBNEIsV0FBVyxrQ0FB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QkFBd0I7QUFFdkIsSUFBTSxZQUErQjtFQUMxQyxXQUFXO0lBQ1QsMkJBQTJCLEVBQUUsaUJBQWlCLEtBQUksQ0FBRTtJQUNwRCxjQUFjLFFBQVEsMEJBQXlCLENBQUU7SUFDakQsa0JBQWtCLGlCQUFpQixDQUFDLGVBQWUsQ0FBQyxDQUFDO0lBQ3JELEVBQUUsU0FBUyxXQUFXLFVBQVUsS0FBSTtJQUNwQztNQUNFLFNBQVM7TUFDVCxVQUFVLHdCQUF3Qix5QkFBeUI7Ozs7OztBTTVCakUsU0FBUyxpQkFBaUI7QUFDMUIsU0FBUyxvQkFBb0I7O0FBYXZCLElBQU8sZUFBUCxNQUFPLGNBQVk7O3FDQUFaLGVBQVk7RUFBQTs2RUFBWixlQUFZLFdBQUEsQ0FBQSxDQUFBLFVBQUEsQ0FBQSxHQUFBLE9BQUEsR0FBQSxNQUFBLEdBQUEsUUFBQSxDQUFBLENBQUEsUUFBQSxpQkFBQSxHQUFBLFdBQUEsR0FBQSxDQUFBLE1BQUEsY0FBQSxDQUFBLEdBQUEsVUFBQSxTQUFBLHNCQUFBLElBQUEsS0FBQTtBQUFBLFFBQUEsS0FBQSxHQUFBO0FBTnJCLE1BQUEsNkJBQUEsR0FBQSxLQUFBLENBQUE7QUFBMEMsTUFBQSxxQkFBQSxHQUFBLDRDQUFBO0FBQU8sTUFBQSwyQkFBQTtBQUNqRCxNQUFBLDZCQUFBLEdBQUEsUUFBQSxDQUFBO0FBQ0UsTUFBQSx3QkFBQSxHQUFBLGVBQUE7QUFDRixNQUFBLDJCQUFBOztvQkFMUSxZQUFZLEdBQUEsZUFBQSxFQUFBLENBQUE7OztnRkFRWCxjQUFZLENBQUE7VUFYeEI7V0FBVTtNQUNULFVBQVU7TUFDVixZQUFZO01BQ1osU0FBUyxDQUFDLFlBQVk7TUFDdEIsVUFBVTs7Ozs7O0tBTVg7Ozs7aUZBQ1ksY0FBWSxFQUFBLFdBQUEsZ0JBQUEsVUFBQSw0QkFBQSxZQUFBLEdBQUEsQ0FBQTtBQUFBLEdBQUE7Ozs7Ozs7K0RBQVosY0FBWSxFQUFBLFNBQUEsQ0FBQUMsR0FBQSxHQUFBLENBQUEsY0FBQSxTQUFBLEdBQUEsYUFBQSxFQUFBLENBQUE7RUFBQTtBQUFBLEdBQUEsT0FBQSxjQUFBLGVBQUEsY0FBQSxxQkFBQSxLQUFBLElBQUEsQ0FBQTtBQUFBLEdBQUEsT0FBQSxjQUFBLGVBQUEsZUFBQSxZQUFBLE9BQUEsWUFBQSxJQUFBLEdBQUEsNEJBQUEsT0FBQSxFQUFBLE9BQUEsTUFBQSxxQkFBQSxFQUFBLFNBQUEsQ0FBQTtBQUFBLEdBQUE7OztBUFZ6QixxQkFBcUIsY0FBYyxTQUFTLEVBQUUsTUFBTSxDQUFDLFFBQVEsUUFBUSxNQUFNLEdBQUcsQ0FBQzsiLCJuYW1lcyI6WyJpbmplY3QiLCJpbmplY3QiLCJpMCJdfQ==