import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Role, ROLE_HOME, ROLE_LABELS } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { GardenSceneComponent } from '../../shared/components/garden-scene/garden-scene.component';

type LoginChannel = 'sms' | 'password' | 'register';

interface FieldStatus {
  type: 'idle' | 'error' | 'success';
  text: string;
}

/**
 * 统一鉴权门户 — 「雨后竹林·月洞门」视觉，多角色 Tab 分页 + 双通道登录 + 注册/入驻申请。
 * 表单交互满足 ADHD 友好性：清晰提示、就地容错、双通道状态标识、无刺眼红框报错。
 */
@Component({
  selector: 'nur-auth-portal',
  standalone: true,
  imports: [GardenSceneComponent],
  templateUrl: './auth-portal.component.html',
  styleUrl: './auth-portal.component.css',
})
export class AuthPortalComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly roles = [Role.STUDENT, Role.PARENT, Role.TEACHER, Role.ADMIN];
  readonly roleLabels = ROLE_LABELS;
  readonly Role = Role;

  readonly activeRole = signal<Role>(Role.STUDENT);
  readonly channel = signal<LoginChannel>('sms');
  readonly submitting = signal(false);
  readonly smsCountdown = signal(0);

  readonly account = signal('');
  readonly password = signal('');
  readonly phone = signal('');
  readonly smsCode = signal('');
  readonly displayName = signal('');
  readonly school = signal('');
  readonly highestDegree = signal('');
  readonly yearsOfTeaching = signal('');

  readonly formStatus = signal<FieldStatus>({ type: 'idle', text: '' });

  /** 就地容错：网络中断或刷新时保全已输入内容 — 表单值驻留内存，路由离开再返回不丢失。 */
  private preserved = new Map<string, string>();

  readonly isRegister = computed(() => this.channel() === 'register');
  readonly registerAvailable = computed(
    () => this.activeRole() === Role.STUDENT || this.activeRole() === Role.PARENT || this.activeRole() === Role.TEACHER,
  );

  readonly smsReady = computed(() => /^1\d{10}$/.test(this.phone()));
  readonly canSubmit = computed(() => {
    if (this.submitting()) {
      return false;
    }
    switch (this.channel()) {
      case 'sms':
        return this.smsReady() && this.smsCode().length === 6;
      case 'password':
        return this.account().length >= 4 && this.password().length >= 8;
      case 'register':
        return (
          this.displayName().length > 0 &&
          this.smsReady() &&
          this.password().length >= 8 &&
          (this.activeRole() !== Role.TEACHER || (this.school().length > 0 && this.highestDegree().length > 0))
        );
    }
  });

  ngOnInit(): void {
    const param = this.route.snapshot.queryParamMap.get('role');
    const role = param ? (param.toUpperCase() as Role) : null;
    if (role && this.roles.includes(role)) {
      this.activeRole.set(role);
    }
  }

  switchRole(role: Role): void {
    if (role === this.activeRole()) {
      return;
    }
    this.preserveCurrentInputs();
    this.activeRole.set(role);
    this.restoreInputs(role);
    this.formStatus.set({ type: 'idle', text: '' });
    if (role === Role.ADMIN || role === Role.PARENT) {
      this.channel.set('password');
    } else if (!this.registerAvailable()) {
      this.channel.set('sms');
    }
  }

  switchChannel(channel: LoginChannel): void {
    this.channel.set(channel);
    this.formStatus.set({ type: 'idle', text: '' });
  }

  async sendSmsCode(): Promise<void> {
    if (!this.smsReady() || this.smsCountdown() > 0) {
      return;
    }
    try {
      await this.auth.sendSmsCode({ role: this.activeRole(), phone: this.phone() }).toPromise();
      this.formStatus.set({
        type: 'success',
        text: '验证码已发送，请注意查收短信。若 60 秒内未收到，可点击重新发送。',
      });
      this.startCountdown(60);
    } catch {
      this.formStatus.set({ type: 'error', text: '验证码发送未成功，请稍后原地重试，已填写内容不会丢失。' });
    }
  }

  async submit(): Promise<void> {
    if (!this.canSubmit()) {
      this.formStatus.set({ type: 'error', text: '还有必填信息未完整，请按上方提示补齐后再试。' });
      return;
    }
    this.submitting.set(true);
    try {
      const res =
        this.channel() === 'sms'
          ? await this.auth.loginBySms({ role: this.activeRole(), phone: this.phone(), smsCode: this.smsCode() }).toPromise()
          : this.channel() === 'password'
            ? await this.auth.loginByPassword({ role: this.activeRole(), account: this.account(), password: this.password() }).toPromise()
            : await this.register().toPromise();
      if (res) {
        this.formStatus.set({ type: 'success', text: `验证通过，正在进入${ROLE_LABELS[res.role]}控制台…` });
        this.auth.navigateToRoleHome(res.role);
      }
    } catch (err: unknown) {
      this.formStatus.set({
        type: 'error',
        text: this.describeError(err) + ' 已输入内容已保全，可原地修改后重试。',
      });
    } finally {
      this.submitting.set(false);
    }
  }

  backToLanding(): void {
    void this.router.navigate(['/']);
  }

  private register() {
    const role = this.activeRole();
    if (role === Role.STUDENT) {
      return this.auth.registerStudent({
        displayName: this.displayName(),
        phone: this.phone(),
        password: this.password(),
      });
    }
    if (role === Role.PARENT) {
      return this.auth.registerParent({
        displayName: this.displayName(),
        phone: this.phone(),
        password: this.password(),
      });
    }
    return this.auth.registerTeacher({
      displayName: this.displayName(),
      phone: this.phone(),
      password: this.password(),
      school: this.school(),
      highestDegree: this.highestDegree(),
      yearsOfTeaching: Number(this.yearsOfTeaching()) || 0,
    });
  }

  private describeError(err: unknown): string {
    const http = err as { error?: { detail?: string; title?: string }; status?: number };
    if (http?.error?.detail) {
      return http.error.detail;
    }
    if (http?.status === 0) {
      return '网络连接似乎中断了。';
    }
    if (http?.status === 401) {
      return '账号或密码与记录不一致。';
    }
    if (http?.status === 409) {
      return '该手机号已注册，请直接登录。';
    }
    return '提交未成功，请稍后重试。';
  }

  private preserveCurrentInputs(): void {
    const key = this.activeRole();
    this.preserved.set(`${key}:account`, this.account());
    this.preserved.set(`${key}:password`, this.password());
    this.preserved.set(`${key}:phone`, this.phone());
    this.preserved.set(`${key}:displayName`, this.displayName());
    this.preserved.set(`${key}:school`, this.school());
    this.preserved.set(`${key}:highestDegree`, this.highestDegree());
    this.preserved.set(`${key}:years`, this.yearsOfTeaching());
  }

  private restoreInputs(role: Role): void {
    this.account.set(this.preserved.get(`${role}:account`) ?? '');
    this.password.set(this.preserved.get(`${role}:password`) ?? '');
    this.phone.set(this.preserved.get(`${role}:phone`) ?? '');
    this.displayName.set(this.preserved.get(`${role}:displayName`) ?? '');
    this.school.set(this.preserved.get(`${role}:school`) ?? '');
    this.highestDegree.set(this.preserved.get(`${role}:highestDegree`) ?? '');
    this.yearsOfTeaching.set(this.preserved.get(`${role}:years`) ?? '');
    this.smsCode.set('');
  }

  private startCountdown(seconds: number): void {
    this.smsCountdown.set(seconds);
    const timer = setInterval(() => {
      const next = this.smsCountdown() - 1;
      this.smsCountdown.set(next);
      if (next <= 0) {
        clearInterval(timer);
      }
    }, 1000);
  }
}
