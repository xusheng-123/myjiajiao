import { environment } from '../../../environments/environment';

/**
 * 统一 REST 契约路径常量 — 人类 Web 与 AI Agent 共享同一套 /api/v1/* 语义。
 * 前缀来自环境配置：开发同源相对路径（dev-server 代理）；生产构建期注入
 * https://api.<域名>/api（Cloudflare Pages + Tunnel 双主机名架构，见 deploy/RUNBOOK.md）。
 * 认证凭据由 auth.interceptor 全局 withCredentials: true 携带，无需逐服务配置。
 */
export const API_BASE = `${environment.apiBaseUrl}/v1`;

export const API = {
  auth: {
    smsCode: `${API_BASE}/identity/auth/sms-codes`,
    loginPassword: `${API_BASE}/identity/auth/login:password`,
    loginSms: `${API_BASE}/identity/auth/login:sms`,
    registerStudent: `${API_BASE}/identity/auth/register:student`,
    registerParent: `${API_BASE}/identity/auth/register:parent`,
    registerTeacher: `${API_BASE}/identity/auth/register:teacher`,
    logout: `${API_BASE}/identity/auth/logout`,
  },
  student: {
    profile: `${API_BASE}/student/me/profile`,
    upcomingLessons: `${API_BASE}/student/me/upcoming-lessons`,
    lessonHistory: `${API_BASE}/student/me/lesson-history`,
    pendingBindings: `${API_BASE}/parent/me/children:pending`,
    confirmBinding: `${API_BASE}/parent/me/children:confirm`,
    focusOverview: `${API_BASE}/student/me/focus-overview`,
    questions: `${API_BASE}/student/me/questions`,
  },
  parent: {
    children: `${API_BASE}/parent/me/children`,
    wallet: `${API_BASE}/parent/me/wallet`,
  },
  teacher: {
    profile: `${API_BASE}/teacher/me/profile`,
    subjects: `${API_BASE}/teacher/me/subjects`,
    pricing: (offeringId: string) => `${API_BASE}/teacher/me/subjects/${offeringId}/pricing`,
    subjectRemove: (offeringId: string) => `${API_BASE}/teacher/me/subjects/${offeringId}`,
    verificationStatus: `${API_BASE}/verification/me/status`,
    verificationSubmit: `${API_BASE}/verification/cases`,
    availabilityCalendar: `${API_BASE}/scheduling/me/calendar`,
    availabilityPublish: `${API_BASE}/scheduling/availability-slots`,
    bookingRequests: `${API_BASE}/teacher/me/booking-requests`,
    decideBooking: (bookingId: string) => `${API_BASE}/teacher/me/booking-requests/${bookingId}:decide`,
    todayLessons: `${API_BASE}/teacher/me/today-lessons`,
    settlement: `${API_BASE}/teacher/me/settlement`,
  },
  admin: {
    overview: `${API_BASE}/admin/overview`,
    accounts: `${API_BASE}/admin/accounts`,
    lockAccount: (accountId: string) => `${API_BASE}/admin/accounts/${accountId}:lock`,
    unlockAccount: (accountId: string) => `${API_BASE}/admin/accounts/${accountId}:unlock`,
    verificationCases: `${API_BASE}/admin/verification-cases`,
    decideVerification: (caseId: string) => `${API_BASE}/admin/verification-cases/${caseId}:decide`,
    refundCases: `${API_BASE}/admin/refund-cases`,
    arbitrationPreview: (caseId: string) => `${API_BASE}/admin/refund-cases/${caseId}/arbitration:preview`,
    arbitrationExecute: (caseId: string) => `${API_BASE}/admin/refund-cases/${caseId}/arbitration:execute`,
    auditLogs: `${API_BASE}/admin/audit-logs`,
    // D1 平台全局参数 / D2 登录限流解锁（2026-10-11 方向 D 重构补齐）
    updateConfig: (key: string) => `${API_BASE}/admin/configs/${key}`,
    resetLoginThrottle: `${API_BASE}/admin/security/login-throttle:reset`,
  },
  matching: {
    search: `${API_BASE}/matching/teachers:search`,
    demands: `${API_BASE}/matching/demands`,
    openDemands: `${API_BASE}/matching/demands:open`,
    favorites: `${API_BASE}/matching/favorites`,
    toggleFavorite: (tid: string) => `${API_BASE}/matching/teachers/${tid}:favorite`,
    recommend: `${API_BASE}/matching/teachers:recommend`,
  },
  scheduling: {
    openSlots: (teacherId: string) => `${API_BASE}/scheduling/teachers/${teacherId}/open-slots`,
  },
  booking: {
    create: `${API_BASE}/booking/bookings`,
    mine: `${API_BASE}/booking/me/bookings`,
    detail: (bookingId: string) => `${API_BASE}/booking/bookings/${bookingId}`,
    cancelPreview: (bookingId: string) => `${API_BASE}/booking/bookings/${bookingId}:cancel/preview`,
    cancelExecute: (bookingId: string) => `${API_BASE}/booking/bookings/${bookingId}:cancel/execute`,
    reschedule: (bookingId: string) => `${API_BASE}/booking/bookings/${bookingId}:reschedule`,
    start: (bookingId: string) => `${API_BASE}/booking/bookings/${bookingId}:start`,
    complete: (bookingId: string) => `${API_BASE}/booking/bookings/${bookingId}:complete`,
  },
  classroom: {
    join: (bookingId: string) => `${API_BASE}/classroom/bookings/${bookingId}/join`,
    // P1-4 教师课堂准备：登记外部会议链接 / 结课 / 考勤
    ensureSession: (bookingId: string) => `${API_BASE}/classroom/bookings/${bookingId}/session`,
    endSession: (bookingId: string) => `${API_BASE}/classroom/bookings/${bookingId}/session:end`,
    attendance: (bookingId: string) => `${API_BASE}/classroom/bookings/${bookingId}/attendance`,
    // B6 课前准备任务：导师生成（POST）与学生/导师查看（GET）
    preparationTasks: (bookingId: string) => `${API_BASE}/classroom/bookings/${bookingId}/preparation-tasks`,
  },
  learning: {
    growth: `${API_BASE}/learning/growth`,
    tasks: `${API_BASE}/learning/tasks`,
    submitTask: (taskId: string) => `${API_BASE}/learning/tasks/${taskId}:submit`,
    archive: `${API_BASE}/learning/archive`,
    profile: `${API_BASE}/learning/profile`,
    questions: `${API_BASE}/learning/questions`,
    closeQuestion: (id: string) => `${API_BASE}/learning/questions/${id}:close`,
    teacherTasks: `${API_BASE}/learning/teacher/tasks`,
    createTask: `${API_BASE}/learning/teacher/tasks`,
    feedbackTask: (taskId: string) => `${API_BASE}/learning/tasks/${taskId}:feedback`,
    createRecord: `${API_BASE}/learning/teacher/lesson-records`,
    myRecords: `${API_BASE}/learning/teacher/lesson-records`,
    myStudents: `${API_BASE}/learning/teacher/students`,
    studentArchive: (sid: string) => `${API_BASE}/learning/teacher/students/${sid}`,
    answerQuestion: (id: string) => `${API_BASE}/learning/questions/${id}:answer`,
    weeklyReport: (sid: string) => `${API_BASE}/parent/me/weekly-report?studentId=${sid}`,
  },
  adminOrders: {
    pending: `${API_BASE}/admin/orders/pending-payment`,
    confirmOffline: (id: string) => `${API_BASE}/admin/orders/${id}:confirm-offline-payment`,
  },
  review: {
    submit: `${API_BASE}/review/reviews`,
    given: `${API_BASE}/review/me/reviews-given`,
    // C1 导师公开评价列表 / C2 导师收到的评价（2026-10-11 方向 D 重构补齐）
    publicList: (revieweeId: string) => `${API_BASE}/review/reviewees/${revieweeId}/reviews`,
    received: `${API_BASE}/review/me/reviews-received`,
  },
  order: {
    create: `${API_BASE}/order/orders`,
    mine: `${API_BASE}/order/me/orders`,
    related: `${API_BASE}/order/me/refundable-orders`,
    orderable: (studentId: string) => `${API_BASE}/order/bookings:orderable?studentId=${studentId}`,
  },
  payment: {
    config: `${API_BASE}/payment/config`,
    initiate: `${API_BASE}/payment/payments:initiate`,
    sandboxNotify: `${API_BASE}/payment/sandbox/notify`,
  },
  messaging: {
    conversations: `${API_BASE}/messaging/conversations`,
    messages: (conversationId: string) => `${API_BASE}/messaging/conversations/${conversationId}/messages`,
    notifications: `${API_BASE}/messaging/notifications`,
  },
} as const;
