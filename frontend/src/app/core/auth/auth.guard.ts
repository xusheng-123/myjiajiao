import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './auth.models';

/**
 * 角色路由守卫 — 依据 JWT 角色凭证保护四端控制台。
 */
export function roleGuard(required: Role): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (auth.isLoggedIn() && auth.role() === required) {
      return true;
    }
    return router.createUrlTree(['/auth'], { queryParams: { role: required.toLowerCase() } });
  };
}
