import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from './endpoints';
import { TeacherSummary } from '../../shared/models/teacher.model';

/**
 * 教师检索查询条件 — 与后端 GET /api/v1/matching/teachers:search 参数逐一对齐
 * （subject / gradeBand / maxPricePerHourCents / styleTag / keyword / includeUnverified）。
 */
export interface TeacherQuery {
  /** 自由关键词：科目/学段/院校包含匹配 */
  keyword?: string;
  /** 学科，如 数学 */
  subject?: string;
  /** 学段，如 初中 */
  gradeBand?: string;
  /** 单价上限（分/小时） */
  maxPricePerHourCents?: number | null;
  /** 教学风格标签 */
  styleTag?: string;
  /** 是否包含未认证导师（默认仅展示已认证） */
  includeUnverified?: boolean;
}

/**
 * 教师检索端口（Mock/HTTP 双态切换的唯一接缝）。
 * 抽象类本身即 DI Token；切换正式 API 时仅需在 app.config.ts 改 useClass，页面代码零改动。
 */
@Injectable()
export abstract class TeachersApi {
  abstract search(query: TeacherQuery): Observable<TeacherSummary[]>;
}

/** 检索实现：正式 HTTP 通道（对应后端 matching 契约）。 */
@Injectable()
export class HttpTeachersApiService extends TeachersApi {
  private readonly http = inject(HttpClient);

  override search(query: TeacherQuery): Observable<TeacherSummary[]> {
    let params = new HttpParams();
    if (query.keyword) params = params.set('keyword', query.keyword);
    if (query.subject) params = params.set('subject', query.subject);
    if (query.gradeBand) params = params.set('gradeBand', query.gradeBand);
    if (query.maxPricePerHourCents != null) {
      params = params.set('maxPricePerHourCents', String(query.maxPricePerHourCents));
    }
    if (query.styleTag) params = params.set('styleTag', query.styleTag);
    return this.http.get<TeacherSummary[]>(`${API_BASE}/matching/public/teachers`, { params });
  }
}
