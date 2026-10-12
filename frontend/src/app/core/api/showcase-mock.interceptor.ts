import { HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { ALL_TEACHERS } from '../../features/teachers/teachers.mock';

/**
 * 纯静态演示模式全局 Mock 拦截器：
 * 当应用作为纯静态站点展示（无 Java/PostgreSQL 后端）时，
 * 拦截所有 /api/v1/* 请求并返回高保真拟真业务数据，确保公开页与四端控制台交互流畅。
 */
export const showcaseMockInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> => {
  const url = req.url;

  // 仅拦截 /api/v1 或相对路径 /api/v1 请求
  if (!url.includes('/api/v1') && !url.includes('/v1/')) {
    return next(req);
  }

  const mockData = resolveMockResponse(req);
  if (mockData !== undefined) {
    // 模拟 120ms 网络延迟，呈现丝滑加载态
    return of(new HttpResponse({ status: 200, body: mockData })).pipe(delay(120));
  }

  // 默认兜底响应（防止 404 中断前端体验）
  if (req.method === 'GET') {
    return of(new HttpResponse({ status: 200, body: [] })).pipe(delay(80));
  }
  return of(new HttpResponse({ status: 200, body: { success: true, message: '【静态演示模式】操作已模拟完成' } })).pipe(
    delay(80),
  );
};

function resolveMockResponse(req: HttpRequest<unknown>): unknown {
  const url = req.url;
  const method = req.method;

  // ---------------------------------------------------- 1. 鉴权与能力端点
  if (url.includes('/identity/auth/capabilities')) {
    return { smsAvailable: true };
  }

  if (url.includes('/identity/auth/login') || url.includes('/identity/auth/register')) {
    const body = (req.body as Record<string, any>) || {};
    const role = body.role || 'STUDENT';
    const names: Record<string, string> = {
      STUDENT: '林微雨 (演示学员)',
      PARENT: '林家长 (演示家长)',
      TEACHER: '沈观澜 (演示导师)',
      ADMIN: '平台运营主管 (演示)',
    };
    return {
      accountNo: `${role}-2026-DEMO`,
      displayName: names[role] || '演示用户',
      role: role,
      accessToken: 'demo-mock-jwt-token-2026',
      refreshToken: 'demo-mock-refresh-token-2026',
      expiresAtEpochMs: Date.now() + 86400000 * 30, // 30天有效
    };
  }

  if (url.includes('/identity/auth/logout')) {
    return { success: true };
  }

  // ---------------------------------------------------- 2. 学生端控制台
  if (url.includes('/student/me/focus-overview')) {
    return {
      nextLesson: {
        bookingId: '01930eee-6a01-7b2e-b1c0-booking001',
        subject: '高中数学 · 导数专题与解题思维',
        teacherName: '沈观澜',
        teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e07',
        startAt: '今晚 19:30 - 21:00',
        status: 'CONFIRMED',
      },
      preparationTasks: [
        { taskId: 'task-01', title: '准备导数专题讲义与草稿纸', sortIndex: 0, completed: true },
        { taskId: 'task-02', title: '花 3 分钟复习上次课错题要点', sortIndex: 1, completed: true },
        { taskId: 'task-03', title: '做 1 道切线斜率热身小题', sortIndex: 2, completed: false },
        { taskId: 'task-04', title: '喝杯温水，调整至专注呼吸状态', sortIndex: 3, completed: false },
      ],
      encouragement: '心静如水，细雨润物。按自己的节奏思考，每前进一步都值得肯定。',
    };
  }

  if (url.includes('/student/me/upcoming-lessons')) {
    return [
      {
        bookingId: '01930eee-6a01-7b2e-b1c0-booking001',
        subject: '高中数学 · 导数专题与解题思维',
        status: 'CONFIRMED',
        type: 'FORMAL',
        startAt: '2026-10-15T19:30:00+08:00',
        teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e07',
      },
      {
        bookingId: '01930eee-6a01-7b2e-b1c0-booking002',
        subject: '高中英语 · 读后续写与长难句剖析',
        status: 'CONFIRMED',
        type: 'FORMAL',
        startAt: '2026-10-17T15:00:00+08:00',
        teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e08',
      },
    ];
  }

  if (url.includes('/student/me/lesson-history')) {
    return [
      {
        bookingId: '01930eee-6a01-7b2e-b1c0-booking003',
        subject: '高中数学 · 函数单调性与极值构造',
        status: 'COMPLETED',
        type: 'FORMAL',
        startAt: '2026-10-10T19:30:00+08:00',
        teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e07',
      },
    ];
  }

  if (url.includes('/student/me/profile')) {
    return {
      accountId: '01930eee-6a01-7b2e-b1c0-student001',
      studentNo: 'STU20261001',
      gradeLevel: '高中二年级',
      learningStyle: '结构推导与启发式互动',
      focusPreference: '番茄工作法（25分钟专注+5分钟微歇）',
      preferredSubjects: ['数学', '物理', '英语'],
    };
  }

  if (url.includes('/student/me/legacy-wallet')) {
    return { balanceCents: 0, content: [], totalPages: 1 };
  }

  if (url.includes('/learning/profile')) {
    return {
      studentId: '01930eee-6a01-7b2e-b1c0-student001',
      goal: '期末数学冲刺 135+，攻克解析几何大题与导数综合压轴',
      stage: '高二强化精进期',
      targetSubjects: '数学, 物理',
    };
  }

  // ---------------------------------------------------- 3. 家长端控制台
  if (url.includes('/parent/me/children:pending')) {
    return [];
  }

  if (url.includes('/parent/me/children')) {
    return [
      {
        bindingId: '01930eee-6a01-7b2e-b1c0-bind001',
        studentId: '01930eee-6a01-7b2e-b1c0-student001',
        studentNo: 'STU20261001',
        status: 'ACTIVE',
        relation: '母子',
      },
    ];
  }

  if (url.includes('/parent/me/wallet')) {
    return {
      balanceCents: 360000,
      currency: 'CNY',
      recentTransactions: [
        {
          txnId: 'txn-001',
          type: 'TOPUP',
          amountCents: 500000,
          balanceAfterCents: 500000,
          memo: '家庭统一教育金充值（在线支付）',
          createdAt: '2026-10-01T10:00:00+08:00',
        },
        {
          txnId: 'txn-002',
          type: 'PAYMENT',
          amountCents: -140000,
          balanceAfterCents: 360000,
          memo: '支付预约课时费（沈观澜 · 高中数学 2 课时）',
          createdAt: '2026-10-05T14:30:00+08:00',
        },
      ],
    };
  }

  if (url.includes('/parent/me/weekly-report')) {
    return {
      studentName: '林微雨',
      period: '2026年10月第一周',
      attendanceRate: '100%',
      completedLessons: 2,
      homeworkSubmissionRate: '100%',
      tutorComments: '本周重点突破导数求导法则与切线斜率问题，思维缜密，草稿步骤严谨。建议继续保持良好专注节奏。',
    };
  }

  // ---------------------------------------------------- 4. 导师端控制台
  if (url.includes('/teacher/me/profile')) {
    return {
      accountId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e07',
      displayName: '沈观澜',
      school: '北京大学',
      highestDegree: '博士',
      yearsOfTeaching: 11,
      intro: '专注高考数学与自招竞赛辅导11年。善于用直观几何直觉拆解复杂代数问题，引导学生自己推导出定理核心。',
      styleTags: ['思维推导', '竞赛辅导', '深入浅出'],
    };
  }

  if (url.includes('/teacher/me/today-lessons')) {
    return [
      {
        bookingId: '01930eee-6a01-7b2e-b1c0-booking001',
        subject: '高中数学 · 导数专题与解题思维',
        startAt: '2026-10-15T19:30:00+08:00',
        endAt: '2026-10-15T21:00:00+08:00',
        status: 'CONFIRMED',
      },
    ];
  }

  if (url.includes('/scheduling/me/calendar')) {
    return [
      {
        slotId: 'slot-001',
        startAt: '2026-10-15T19:30:00+08:00',
        endAt: '2026-10-15T21:00:00+08:00',
        status: 'BOOKED',
      },
      {
        slotId: 'slot-002',
        startAt: '2026-10-16T19:30:00+08:00',
        endAt: '2026-10-16T21:00:00+08:00',
        status: 'OPEN',
      },
      {
        slotId: 'slot-003',
        startAt: '2026-10-18T14:00:00+08:00',
        endAt: '2026-10-18T15:30:00+08:00',
        status: 'OPEN',
      },
    ];
  }

  if (url.includes('/teacher/me/booking-requests')) {
    return {
      content: [
        {
          bookingId: '01930eee-6a01-7b2e-b1c0-req001',
          studentId: '01930eee-6a01-7b2e-b1c0-student001',
          studentName: '林微雨',
          subject: '高中数学 · 空间解析几何',
          type: 'FORMAL',
          status: 'CONFIRMED',
          createdAt: '2026-10-12T09:00:00+08:00',
        },
      ],
      totalPages: 1,
    };
  }

  if (url.includes('/teacher/me/subjects')) {
    return [
      {
        offeringId: 'off-001',
        subject: '高中数学',
        gradeBand: '高中',
        pricePerHourCents: 39800,
        active: true,
      },
      {
        offeringId: 'off-002',
        subject: '初中数学',
        gradeBand: '初中',
        pricePerHourCents: 29800,
        active: true,
      },
    ];
  }

  if (url.includes('/teacher/me/settlement')) {
    return {
      pendingNetCents: 33830,
      paidOutNetCents: 169150,
      entries: [
        {
          entryId: 'ent-001',
          bookingId: '01930eee-6a01-7b2e-b1c0-booking003',
          grossCents: 39800,
          platformFeeCents: 5970,
          netCents: 33830,
          status: 'SETTLED',
          settledAt: '2026-10-11T10:00:00+08:00',
          entryKind: 'EARNING',
          reversesEntryId: null,
        },
      ],
    };
  }

  if (url.includes('/verification/me/status')) {
    return {
      status: 'APPROVED',
      decisionNote: '学历与教师资格审核已通过，资质认证标识已上架展示',
      submittedAt: '2026-09-01T10:00:00+08:00',
      decidedAt: '2026-09-02T15:00:00+08:00',
      guidance: '资质认证有效中，可随时发布授课档期。',
    };
  }

  // ---------------------------------------------------- 5. 运营管理控制台
  if (url.includes('/admin/overview')) {
    return {
      totalTeachers: 24,
      approvedTeachers: 20,
      totalStudents: 386,
      totalLessonsCompleted: 1520,
      totalGrossCents: 45860000,
      totalPlatformFeeCents: 6879000,
      pendingVerificationCount: 2,
      pendingRefundCount: 1,
    };
  }

  if (url.includes('/admin/verification-cases')) {
    return {
      content: [
        {
          caseId: 'ver-001',
          teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e07',
          teacherName: '沈观澜',
          school: '北京大学',
          status: 'APPROVED',
          decisionNote: '博士学位证书与教师资格审核无误，予以通过',
          submittedAt: '2026-09-01T10:00:00+08:00',
        },
        {
          caseId: 'ver-002',
          teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e08',
          teacherName: '顾清禾',
          school: '复旦大学',
          status: 'APPROVED',
          decisionNote: '硕士证书清晰，审核通过',
          submittedAt: '2026-09-03T14:20:00+08:00',
        },
      ],
      totalPages: 1,
    };
  }

  if (url.includes('/admin/refund-cases')) {
    return {
      content: [
        {
          caseId: 'ref-001',
          orderId: '01930eee-6a01-7b2e-b1c0-order001',
          status: 'RESOLVED',
          computedAmountCents: 39800,
          reason: '因突发校内考试调课冲突，协商一致友好申请退款',
          decisionNote: '经师生双方确认同意全额退款',
        },
      ],
      totalPages: 1,
    };
  }

  if (url.includes('/admin/accounts')) {
    return {
      content: [
        {
          accountId: '01930eee-6a01-7b2e-b1c0-student001',
          role: 'STUDENT',
          displayName: '林微雨',
          phone: '138****0001',
          accountNo: 'STU20261001',
          status: 'ACTIVE',
        },
        {
          accountId: '01930eee-6a01-7b2e-b1c0-parent001',
          role: 'PARENT',
          displayName: '林家长',
          phone: '138****0002',
          accountNo: 'PAR20261001',
          status: 'ACTIVE',
        },
        {
          accountId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e07',
          role: 'TEACHER',
          displayName: '沈观澜',
          phone: '138****0003',
          accountNo: 'TCH20261001',
          status: 'ACTIVE',
        },
        {
          accountId: '01930eee-6a01-7b2e-b1c0-admin001',
          role: 'ADMIN',
          displayName: '平台管理员',
          phone: '138****0000',
          accountNo: 'ADM20260001',
          status: 'ACTIVE',
        },
      ],
      totalPages: 1,
    };
  }

  if (url.includes('/admin/audit-logs')) {
    return {
      content: [
        {
          auditId: 'aud-001',
          callerId: '01930eee-6a01-7b2e-b1c0-admin001',
          callerRole: 'ADMIN',
          occurredAt: '2026-10-12T09:30:00+08:00',
          actionType: 'VERIFICATION_APPROVAL',
          executionStatus: 'SUCCESS',
          payloadHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
        },
        {
          auditId: 'aud-002',
          callerId: '01930eee-6a01-7b2e-b1c0-parent001',
          callerRole: 'PARENT',
          occurredAt: '2026-10-12T09:15:00+08:00',
          actionType: 'WALLET_TOPUP',
          executionStatus: 'SUCCESS',
          payloadHash: '7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069',
        },
      ],
      totalPages: 1,
    };
  }

  // ---------------------------------------------------- 6. 订单、预约与评价
  if (url.includes('/booking/me/bookings')) {
    return {
      content: [
        {
          bookingId: '01930eee-6a01-7b2e-b1c0-booking001',
          studentId: '01930eee-6a01-7b2e-b1c0-student001',
          teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e07',
          slotId: 'slot-001',
          subject: '高中数学 · 导数专题',
          type: 'FORMAL',
          status: 'CONFIRMED',
          priceCents: 39800,
          cancelReason: null,
          createdAt: '2026-10-10T12:00:00+08:00',
        },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    };
  }

  if (url.includes('/order/me/orders')) {
    return {
      content: [
        {
          orderId: '01930eee-6a01-7b2e-b1c0-order001',
          orderNo: 'ORD20261010001',
          bookingId: '01930eee-6a01-7b2e-b1c0-booking001',
          status: 'PAID',
          amountCents: 39800,
          paidAt: '2026-10-10T12:05:00+08:00',
        },
      ],
      totalPages: 1,
      totalElements: 1,
    };
  }

  if (url.includes('/order/me/refundable-orders') || url.includes('/order/bookings:orderable')) {
    return [];
  }

  if (url.includes('/payment/config')) {
    return { sandboxEnabled: true };
  }

  // 教师公开详情与评价
  if (url.includes('/matching/public/teachers/')) {
    const parts = url.split('/');
    const teacherId = parts[parts.length - 1]?.split('?')[0];
    const found = ALL_TEACHERS.find((t) => t.teacherId === teacherId) || ALL_TEACHERS[0];
    return {
      teacherId: found.teacherId,
      displayName: found.displayName,
      school: found.school,
      highestDegree: found.highestDegree,
      yearsOfTeaching: found.yearsOfTeaching,
      intro: `${found.displayName}老师，毕业于${found.school}（${found.highestDegree}），拥有${found.yearsOfTeaching}年一线辅导经验。讲课风格以${found.styleTags.join('、')}见长，善于因材施教。`,
      styleTags: found.styleTags,
      averageRating: found.averageRating,
      reviewCount: found.reviewCount,
      offerings: [
        { subject: found.subject, gradeBand: found.gradeBand, pricePerHourCents: found.pricePerHourCents },
      ],
      openSlots: [
        { slotId: 'slot-mock-1', startAt: '2026-10-15T19:30:00+08:00', endAt: '2026-10-15T21:00:00+08:00' },
        { slotId: 'slot-mock-2', startAt: '2026-10-17T15:00:00+08:00', endAt: '2026-10-17T16:30:00+08:00' },
      ],
    };
  }

  if (url.includes('/review/reviewees/')) {
    return [
      {
        reviewId: 'rev-001',
        bookingId: '01930eee-6a01-7b2e-b1c0-booking003',
        direction: 'STUDENT_TO_TEACHER',
        rating: 5,
        tags: ['思路清晰', '解题有启发', '耐心温和'],
        comment: '老师讲解特别透彻，不仅给了解题模板，更把背后的函数图像变化规律分析得非常生动！',
        createdAt: '2026-10-10T21:00:00+08:00',
      },
      {
        reviewId: 'rev-002',
        bookingId: '01930eee-6a01-7b2e-b1c0-booking002',
        direction: 'STUDENT_TO_TEACHER',
        rating: 5,
        tags: ['重点突出', '方法实用'],
        comment: '针对薄弱点辅导非常精准，上课节奏舒适，学习信心大增！',
        createdAt: '2026-10-08T18:30:00+08:00',
      },
    ];
  }

  if (url.includes('/review/me/')) {
    return [];
  }

  // ---------------------------------------------------- 7. 通用写操作与状态变更拟真
  if (method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE') {
    return {
      success: true,
      status: 'SUCCESS',
      message: '【静态演示模式】操作已模拟保存并在当前前端会话生效',
    };
  }

  return undefined;
}
