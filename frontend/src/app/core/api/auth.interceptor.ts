import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { HttpHandlerFn, HttpRequest, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * 鉴权拦截器 — 附加 JWT Bearer 凭证与写操作幂等键。
 */
export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> => {
  const auth = inject(AuthService);
  const session = auth.session();

  let request = req;
  // D5：会话令牌经 HttpOnly Cookie 自动携带（跨源部署需 credentials）；内存令牌存在时仍用 Bearer（API 客户端语义）
  request = request.clone({ withCredentials: true });
  if (session?.accessToken) {
    request = request.clone({ setHeaders: { Authorization: `Bearer ${session.accessToken}` } });
  }
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method)) {
    // 强幂等性保障：所有有状态写操作必须携带 Idempotency-Key (UUIDv4)
    request = request.clone({ setHeaders: { 'Idempotency-Key': crypto.randomUUID() } });
  }
  return next(request);
};
