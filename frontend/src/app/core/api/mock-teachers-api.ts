import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { TeachersApi, TeacherQuery } from './teachers-api';
import { TeacherSummary } from '../../shared/models/teacher.model';
import { ALL_TEACHERS } from '../../features/teachers/teachers.mock';

/**
 * 教师检索 Mock 实现 — 数据源为 20 名内置教师（规范二十九）。
 * 筛选与排序语义逐条镜像后端 MatchingApplicationService.search：
 * 认证默认过滤、价格上限、学段精确、风格标签精确、关键词包含匹配；
 * 排序为透明证据序（认证 > 评价证据 > 可约时间），不虚构匹配百分比。
 * 250ms 模拟时延用于演练与 HTTP 一致的加载态。
 */
@Injectable()
export class MockTeachersApiService extends TeachersApi {

  override search(query: TeacherQuery): Observable<TeacherSummary[]> {
    const keyword = query.keyword?.trim();
    const list = ALL_TEACHERS.filter((t) => {
      if (!query.includeUnverified && !t.verificationApproved) {
        return false;
      }
      if (query.subject && !t.subject.includes(query.subject)) {
        return false;
      }
      if (query.gradeBand && t.gradeBand !== query.gradeBand) {
        return false;
      }
      if (query.maxPricePerHourCents != null && t.pricePerHourCents > query.maxPricePerHourCents) {
        return false;
      }
      if (query.styleTag && !t.styleTags.includes(query.styleTag)) {
        return false;
      }
      if (keyword) {
        const haystack = `${t.subject} ${t.gradeBand} ${t.school} ${t.displayName}`;
        if (!haystack.includes(keyword)) {
          return false;
        }
      }
      return true;
    });

    const sorted = [...list].sort((a, b) => evidenceScore(b) - evidenceScore(a));
    // 模拟网络时延，驱动页面的加载骨架态
    return of(sorted).pipe(delay(250));
  }
}

/** 与后端 evidenceScore 对齐：认证 2 分 + 有评价 1 分 + 有可约档期 1 分。 */
function evidenceScore(t: TeacherSummary): number {
  let score = 0;
  if (t.verificationApproved) score += 2;
  if (t.reviewCount > 0) score += 1;
  if (t.nextOpenSlotAt) score += 1;
  return score;
}
