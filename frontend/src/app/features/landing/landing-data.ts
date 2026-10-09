import { TeacherSummary } from '../../shared/models/teacher.model';

/**
 * 首页开发期 Mock 数据（规范二十九）：
 * 结构与正式 API 完全一致，切 API 时只换数据来源，不改页面。
 */

/** 精选导师（GET /api/v1/teachers 返回项形状） */
export const FEATURED_TEACHERS: TeacherSummary[] = [
  {
    teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e01',
    displayName: '林晚舟',
    school: '清华大学',
    highestDegree: '硕士',
    yearsOfTeaching: 8,
    subject: '数学',
    gradeBand: '高中',
    pricePerHourCents: 26800,
    verificationApproved: true,
    averageRating: 4.9,
    reviewCount: 132,
    nextOpenSlotAt: '2026-09-29T19:00:00+08:00',
    styleTags: ['思维推导', '启发式提问', '耐心细致'],
  },
  {
    teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e02',
    displayName: '沈砚秋',
    school: '北京大学',
    highestDegree: '学士',
    yearsOfTeaching: 6,
    subject: '语文',
    gradeBand: '高中',
    pricePerHourCents: 22800,
    verificationApproved: true,
    averageRating: 4.8,
    reviewCount: 98,
    nextOpenSlotAt: '2026-09-28T18:30:00+08:00',
    styleTags: ['文本细读', '写作思维', '文化视野'],
  },
  {
    teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e03',
    displayName: '顾之行',
    school: '复旦大学',
    highestDegree: '硕士',
    yearsOfTeaching: 5,
    subject: '英语',
    gradeBand: '初中',
    pricePerHourCents: 19800,
    verificationApproved: true,
    averageRating: 4.9,
    reviewCount: 156,
    nextOpenSlotAt: '2026-09-30T20:00:00+08:00',
    styleTags: ['情景对话', '语音纠正', '兴趣引导'],
  },
  {
    teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e04',
    displayName: '陆知遥',
    school: '中国科学技术大学',
    highestDegree: '博士',
    yearsOfTeaching: 9,
    subject: '物理',
    gradeBand: '高中',
    pricePerHourCents: 29800,
    verificationApproved: true,
    averageRating: 5.0,
    reviewCount: 87,
    nextOpenSlotAt: '2026-10-01T19:30:00+08:00',
    styleTags: ['模型构建', '竞赛思维', '深入浅出'],
  },
  {
    teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e05',
    displayName: '苏见微',
    school: '南京大学',
    highestDegree: '硕士',
    yearsOfTeaching: 7,
    subject: '化学',
    gradeBand: '高中',
    pricePerHourCents: 24800,
    verificationApproved: true,
    averageRating: 4.7,
    reviewCount: 64,
    nextOpenSlotAt: '2026-09-29T18:00:00+08:00',
    styleTags: ['实验探究', '知识网络', '稳扎稳打'],
  },
  {
    teacherId: '01930eee-6a01-7b2e-b1c0-1a2b3c4d5e06',
    displayName: '程亦安',
    school: '浙江大学',
    highestDegree: '学士',
    yearsOfTeaching: 3,
    subject: '信息学',
    gradeBand: '高中',
    pricePerHourCents: 31800,
    verificationApproved: true,
    averageRating: null,
    reviewCount: 0,
    nextOpenSlotAt: '2026-09-30T19:00:00+08:00',
    styleTags: ['算法思维', '项目实战', '竞赛辅导'],
  },
];

/** 办学理念三则 */
export const PRINCIPLES: ReadonlyArray<{ title: string; text: string }> = [
  { title: '因材施教', text: '一人一策，不设统一模板。' },
  { title: '循循善诱', text: '先扶一程，再放一步。' },
  { title: '静待花开', text: '尊重节奏，拒绝焦虑灌输。' },
];

/** 服务体系 */
export const SERVICES: ReadonlyArray<{ no: string; title: string; text: string }> = [
  {
    no: '壹',
    title: '课后答疑',
    text: '名校学长一对一即时解惑，疑难点当堂消解，不留隔夜题。',
  },
  {
    no: '贰',
    title: '学业精修',
    text: '资深导师定制思维推导路径，直击瓶颈，拒绝机械刷题。',
  },
  {
    no: '叁',
    title: '成长陪伴',
    text: '课后学情反馈与阶段规划，让每一步努力都有回响。',
  },
];

/** 学科门类 */
export const SUBJECTS: ReadonlyArray<string> = [
  '语文',
  '数学',
  '英语',
  '物理',
  '化学',
  '生物',
  '历史',
  '地理',
  '政治',
  '信息学',
  '竞赛编程',
];

/** 学习流程六步闭环（规范十三） */
export const PROCESS_STEPS: ReadonlyArray<{ no: string; title: string; text: string }> = [
  { no: '01', title: '寻访良师', text: '按学科、学段与教学风格，安静筛选，从容比较。' },
  { no: '02', title: '沟通试听', text: '先聊后学，一节试听课确认彼此节奏。' },
  { no: '03', title: '预约确认', text: '档期锁定，改期与取消皆有章法。' },
  { no: '04', title: '安心支付', text: '平台托管课时费，消费透明可查。' },
  { no: '05', title: '如期开课', text: '线上教室准时开讲，课前提醒不错过。' },
  { no: '06', title: '反馈复购', text: '每课沉淀学情反馈，进步有迹可循。' },
];

/** 学员与家长的声音 */
export const VOICES: ReadonlyArray<{ quote: string; name: string; role: string }> = [
  {
    quote: '以前刷题靠蛮力，现在老师带我拆解每一类题的思维路径，数学从 92 分爬到了 128。',
    name: '陈同学',
    role: '高三 · 数学精修',
  },
  {
    quote: '最打动我的是课后反馈，每一次课都有一条安静的记录，孩子的变化看得见。',
    name: '李女士',
    role: '初二学生家长',
  },
  {
    quote: '答疑像及时雨，卡壳的题当晚就通了。竞赛路上有人同行，心里不慌。',
    name: '赵同学',
    role: '高一 · 物理竞赛',
  },
];
