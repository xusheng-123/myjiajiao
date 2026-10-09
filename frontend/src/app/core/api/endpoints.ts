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
    preparationTasks: (lessonId: string) => `${API_BASE}/student/me/lessons/${lessonId}/preparation-tasks`,
    completeTask: (taskId: string) => `${API_BASE}/student/me/preparation-tasks/${taskId}:complete`,
    focusOverview: `${API_BASE}/student/me/focus-overview`,
    questions: `${API_BASE}/student/me/questions`,
  },
  parent: {
    children: `${API_BASE}/parent/me/children`,
    childOverview: (studentId: string) => `${API_BASE}/parent/me/children/${studentId}/overview`,
    wallet: `${API_BASE}/parent/me/wallet`,
    walletTransactions: `${API_BASE}/parent/me/wallet/transactions`,
    lessonProgress: `${API_BASE}/parent/me/lesson-progress`,
    reviews: `${API_BASE}/parent/me/reviews`,
  },
  teacher: {
    profile: `${API_BASE}/teacher/me/profile`,
    subjects: `${API_BASE}/teacher/me/subjects`,
    pricing: (offeringId: string) => `${API_BASE}/teacher/me/subjects/${offeringId}/pricing`,
    verificationStatus: `${API_BASE}/verification/me/status`,
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
  },
  matching: {
    search: `${API_BASE}/matching/teachers:search`,
    demands: `${API_BASE}/matching/demands`,
    openDemands: `${API_BASE}/matching/demands:open`,
  },
  scheduling: {
    openSlots: (teacherId: string) => `${API_BASE}/scheduling/teachers/${teacherId}/open-slots`,
  },
  booking: {
    create: `${API_BASE}/booking/bookings`,
    cancelPreview: (bookingId: string) => `${API_BASE}/booking/bookings/${bookingId}:cancel/preview`,
    cancelExecute: (bookingId: string) => `${API_BASE}/booking/bookings/${bookingId}:cancel/execute`,
    reschedule: (bookingId: string) => `${API_BASE}/booking/bookings/${bookingId}:reschedule`,
  },
  classroom: {
    join: (bookingId: string) => `${API_BASE}/classroom/bookings/${bookingId}/join`,
    // P1-4 教师课堂准备：登记外部会议链接 / 结课 / 考勤
    ensureSession: (bookingId: string) => `${API_BASE}/classroom/bookings/${bookingId}/session`,
    endSession: (bookingId: string) => `${API_BASE}/classroom/bookings/${bookingId}/session:end`,
    attendance: (bookingId: string) => `${API_BASE}/classroom/bookings/${bookingId}/attendance`,
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
    teacherQuestions: `${API_BASE}/learning/teacher/questions`,
    answerQuestion: (id: string) => `${API_BASE}/learning/questions/${id}:answer`,
    weeklyReport: (sid: string) => `${API_BASE}/parent/me/weekly-report?studentId=${sid}`,
    favorites: `${API_BASE}/matching/favorites`,
    toggleFavorite: (tid: string) => `${API_BASE}/matching/teachers/${tid}:favorite`,
    recommend: `${API_BASE}/matching/teachers:recommend`,
  },
  adminOrders: {
    pending: `${API_BASE}/admin/orders/pending-payment`,
    confirmOffline: (id: string) => `${API_BASE}/admin/orders/${id}:confirm-offline-payment`,
  },
  review: {
    submit: `${API_BASE}/review/reviews`,
    given: `${API_BASE}/review/me/reviews-given`,
  },
  messaging: {
    conversations: `${API_BASE}/messaging/conversations`,
    messages: (conversationId: string) => `${API_BASE}/messaging/conversations/${conversationId}/messages`,
    notifications: `${API_BASE}/messaging/notifications`,
  },
} as const;
