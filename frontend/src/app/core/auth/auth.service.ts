import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import {
  AuthResponse, StoredSession,
  AuthSession,
  LoginByPasswordRequest,
  LoginBySmsRequest,
  RegisterStudentRequest,
  RegisterTeacherRequest,
  Role,
  ROLE_HOME,
  SmsCodeRequest,
} from './auth.models';
import { API } from '../api/endpoints';

const SESSION_KEY = 'nurture.session';
/** D5：localStorage 仅存会话摘要（无令牌）；访问/刷新令牌由 HttpOnly Cookie 承载。 */

/**
 * 统一鉴权服务 — 手机号/验证码、账号/密码双通道登录，JWT 会话管理。
 * 登录成功后依据后端签发的角色凭证执行无缝路由分流。
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly sessionSignal = signal<AuthSession | null>(this.restore());

  readonly session = this.sessionSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.sessionSignal() !== null);
  readonly role = computed(() => this.sessionSignal()?.role ?? null);
  readonly displayName = computed(() => this.sessionSignal()?.displayName ?? '');

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  sendSmsCode(req: SmsCodeRequest): Observable<void> {
    return this.http.post<void>(API.auth.smsCode, req);
  }

  loginByPassword(req: LoginByPasswordRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(API.auth.loginPassword, req)
      .pipe(tap((res) => this.establish(res)));
  }

  loginBySms(req: LoginBySmsRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(API.auth.loginSms, req)
      .pipe(tap((res) => this.establish(res)));
  }

  registerStudent(req: RegisterStudentRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(API.auth.registerStudent, req)
      .pipe(tap((res) => this.establish(res)));
  }

  /** 家长为独立实体账户，与学生注册通道物理分离。 */
  registerParent(req: RegisterStudentRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(API.auth.registerParent, req)
      .pipe(tap((res) => this.establish(res)));
  }

  registerTeacher(req: RegisterTeacherRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(API.auth.registerTeacher, req)
      .pipe(tap((res) => this.establish(res)));
  }

  logout(): void {
    // 刷新令牌经 HttpOnly Cookie 回传（后端吊销并清除 Cookie）；体字段已非必需
    this.http.post(API.auth.logout, {}).subscribe();
    this.sessionSignal.set(null);
    localStorage.removeItem(SESSION_KEY);
    void this.router.navigate(['/']);
  }

  /** 登录成功后的角色分流：依据 JWT 角色凭证无缝跳转对应控制台。 */
  navigateToRoleHome(role: Role): void {
    void this.router.navigateByUrl(ROLE_HOME[role]);
  }

  private establish(res: AuthResponse): void {
    // 令牌仅在内存信号中留存；Cookie 由后端在响应中下发（HttpOnly）
    this.sessionSignal.set(res);
    this.persistSummary(res);
  }

  private persistSummary(res: AuthResponse): void {
    const summary: StoredSession = {
      accountNo: res.accountNo,
      displayName: res.displayName,
      role: res.role,
      expiresAtEpochMs: res.expiresAtEpochMs,
    };
    localStorage.setItem(SESSION_KEY, JSON.stringify(summary));
  }

  private restore(): AuthSession | null {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      if (!raw) {
        return null;
      }
      const summary = JSON.parse(raw) as StoredSession;
      if (summary.expiresAtEpochMs <= Date.now()) {
        localStorage.removeItem(SESSION_KEY);
        return null;
      }
      // 摘要恢复：令牌不在 localStorage（由 Cookie 承载），信号内存中重建会话视图
      return { ...summary };
    } catch {
      return null;
    }
  }
}
