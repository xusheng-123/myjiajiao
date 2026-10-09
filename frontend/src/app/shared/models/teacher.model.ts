/**
 * 教师列表项视图模型。
 * 与后端 `GET /api/v1/teachers` 列表项逐字段对齐
 * （matching.TeacherCandidate × teacher.ProfileResponse 聚合视图，Jackson camelCase）。
 * 切换正式 API 时不得变更本结构与使用它的页面。
 */
export interface TeacherSummary {
  teacherId: string;
  /** 展示名（如 林晚舟） */
  displayName: string;
  school: string;
  /** 最高学历（学士/硕士/博士） */
  highestDegree: string;
  yearsOfTeaching: number;
  /** 主授学科（语文/数学/…） */
  subject: string;
  /** 学段（小学/初中/高中） */
  gradeBand: string;
  /** 课时单价，单位：分 */
  pricePerHourCents: number;
  /** 平台认证是否通过 */
  verificationApproved: boolean;
  /** 综合评分（1.0 – 5.0），尚无评价时为 null */
  averageRating: number | null;
  reviewCount: number;
  /** 最近可约时段（ISO 8601），暂无档期时为 null */
  nextOpenSlotAt: string | null;
  /** 教学风格标签 */
  styleTags: string[];
}
