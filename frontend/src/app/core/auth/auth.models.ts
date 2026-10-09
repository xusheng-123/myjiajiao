/**
 * 鉴权领域模型 — 与后端 identity 模块签发的 JWT 载荷语义对齐。
 */

export enum Role {
  STUDENT = 'STUDENT',
  PARENT = 'PARENT',
  TEACHER = 'TEACHER',
  ADMIN = 'ADMIN',
}

export const ROLE_LABELS: Record<Role, string> = {
  [Role.STUDENT]: '学生',
  [Role.PARENT]: '家长',
  [Role.TEACHER]: '导师',
  [Role.ADMIN]: '运营',
};

export const ROLE_HOME: Record<Role, string> = {
  [Role.STUDENT]: '/student/dashboard',
  [Role.PARENT]: '/parent/dashboard',
  [Role.TEACHER]: '/teacher/dashboard',
  [Role.ADMIN]: '/admin/dashboard',
};

/**
 * Web 会话模型（D5 迁移）：令牌由后端 HttpOnly Cookie 承载（JS 不可读），
 * 本模型仅持久化非敏感展示信息；accessToken/refreshToken 仅存在于登录瞬间的内存信号中。
 */
export interface AuthSession {
  accessToken?: string;
  refreshToken?: string;
  accountNo: string;
  displayName: string;
  role: Role;
  expiresAtEpochMs: number;
}

/** 持久化到 localStorage 的会话摘要（不含任何令牌）。 */
export interface StoredSession {
  accountNo: string;
  displayName: string;
  role: Role;
  expiresAtEpochMs: number;
}

export interface LoginByPasswordRequest {
  role: Role;
  account: string;
  password: string;
}

export interface LoginBySmsRequest {
  role: Role;
  phone: string;
  smsCode: string;
}

export interface SmsCodeRequest {
  role: Role;
  phone: string;
}

export interface RegisterStudentRequest {
  displayName: string;
  phone: string;
  password: string;
}

export interface RegisterTeacherRequest {
  displayName: string;
  phone: string;
  password: string;
  school: string;
  highestDegree: string;
  yearsOfTeaching: number;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  accountNo: string;
  displayName: string;
  role: Role;
  expiresAtEpochMs: number;
}
