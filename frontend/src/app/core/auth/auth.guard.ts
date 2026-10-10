import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './auth.models';

export const sessionGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() || inject(Router).createUrlTree(['/auth']);
};

export const refundGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() && (auth.role() === Role.STUDENT || auth.role() === Role.PARENT)
    || inject(Router).createUrlTree(['/auth']);
};

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
