import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RevealDirective } from '../../shared/directives/reveal.directive';
import { TeacherCardComponent } from '../../shared/components/teacher-card/teacher-card.component';
import { TeachersApi, TeacherQuery } from '../../core/api/teachers-api';
import { TeacherSummary } from '../../shared/models/teacher.model';
import { TeacherCompareComponent } from '../../shared/components/teacher-compare.component';

/** 价格档位（分/小时） */
interface PriceBand {
  label: string;
  value: number | null;
}

type SortKey = 'recommended' | 'price-asc' | 'price-desc' | 'rating-desc' | 'reviews-desc';

/** 高频维度选项（规范十七：默认只展示高频，高级维度折叠） */
const SUBJECT_OPTIONS = ['语文', '数学', '英语', '物理', '化学', '生物', '历史', '地理', '信息学'];
const GRADE_BAND_OPTIONS = ['小学', '初中', '高中'];
const STYLE_TAG_OPTIONS = [
  '耐心细致', '启发式提问', '思维推导', '深入浅出', '兴趣引导', '情景对话',
  '实验探究', '稳扎稳打', '考点拆解', '竞赛辅导', '写作思维', '知识网络',
];
const PRICE_BANDS: PriceBand[] = [
  { label: '不限', value: null },
  { label: '≤100 元', value: 10000 },
  { label: '≤200 元', value: 20000 },
  { label: '≤300 元', value: 30000 },
];
const SORT_OPTIONS: Array<{ key: SortKey; label: string }> = [
  { key: 'recommended', label: '平台推荐排序' },
  { key: 'price-asc', label: '价格从低到高' },
  { key: 'price-desc', label: '价格从高到低' },
  { key: 'rating-desc', label: '评分最高' },
  { key: 'reviews-desc', label: '评价最多' },
];

/**
 * 教师列表页（规范十七）— 搜索 + 全维度组合筛选，复用 nur-teacher-card。
 * 四态分明：加载骨架 / 请求失败可重试 / 结果为空可清条件 / 结果网格。
 * 数据经 TEACHERS_API 端口注入（Mock/HTTP 双态），页面代码与数据来源解耦。
 */
@Component({
  selector: 'nur-teachers-page',
  standalone: true,
  imports: [FormsModule, RevealDirective, TeacherCardComponent, TeacherCompareComponent],
  templateUrl: './teachers.component.html',
  styleUrl: './teachers.component.css',
})
export class TeachersComponent {
  readonly compared = signal<TeacherSummary[]>([]);
  readonly compareNotice = signal('');
  isCompared(id: string): boolean { return this.compared().some(t => t.teacherId === id); }
  toggleCompare(t: TeacherSummary): void {
    if (this.compared().some(c => c.teacherId === t.teacherId)) { this.removeCompare(t.teacherId); return; }
    if (this.compared().length >= 3) { this.compareNotice.set('最多对比 3 位老师，请先移除一位。'); return; }
    this.compared.update(rows => [...rows, t]); this.compareNotice.set('');
  }
  removeCompare(id: string): void { this.compared.update(rows => rows.filter(t => t.teacherId !== id)); this.compareNotice.set(''); }
  private readonly teachersApi = inject(TeachersApi);

  // ---------- 筛选状态 ----------
  readonly subjectOptions = SUBJECT_OPTIONS;
  readonly gradeBandOptions = GRADE_BAND_OPTIONS;
  readonly styleTagOptions = STYLE_TAG_OPTIONS;
  readonly priceBands = PRICE_BANDS;
  readonly sortOptions = SORT_OPTIONS;

  readonly keywordInput = signal('');
  private readonly debouncedKeyword = signal('');
  readonly subject = signal('');
  readonly gradeBand = signal('');
  readonly maxPrice = signal<number | null>(null);
  readonly styleTag = signal('');
  readonly includeUnverified = signal(false);
  readonly advancedOpen = signal(false);
  readonly sortKey = signal<SortKey>('recommended');

  // ---------- 结果状态（四态：loading / error / empty / ready） ----------
  readonly results = signal<TeacherSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  private readonly query = computed<TeacherQuery>(() => ({
    keyword: this.debouncedKeyword().trim() || undefined,
    subject: this.subject() || undefined,
    gradeBand: this.gradeBand() || undefined,
    maxPricePerHourCents: this.maxPrice(),
    styleTag: this.styleTag() || undefined,
    includeUnverified: this.includeUnverified(),
  }));

  constructor() {
    // 关键词 300ms 防抖，避免每敲一个字就全量检索
    effect(() => {
      const kw = this.keywordInput();
      const timer = setTimeout(() => this.debouncedKeyword.set(kw), 300);
      return () => clearTimeout(timer);
    });
    // 任一筛选条件变化即重新检索（loading/error/results 不在依赖里，untracked 隔离）
    effect(() => {
      const q = this.query();
      untracked(() => this.load(q));
    });
  }

  // ---------- 派生态 ----------
  readonly activeFilterCount = computed(() => {
    let count = 0;
    if (this.debouncedKeyword().trim()) count++;
    if (this.subject()) count++;
    if (this.gradeBand()) count++;
    if (this.maxPrice() != null) count++;
    if (this.styleTag()) count++;
    if (this.includeUnverified()) count++;
    return count;
  });

  readonly visibleTeachers = computed(() => {
    const list = [...this.results()];
    switch (this.sortKey()) {
      case 'price-asc':
        return list.sort((a, b) => a.pricePerHourCents - b.pricePerHourCents);
      case 'price-desc':
        return list.sort((a, b) => b.pricePerHourCents - a.pricePerHourCents);
      case 'rating-desc':
        return list.sort((a, b) =>
          (b.averageRating ?? 0) - (a.averageRating ?? 0) || b.reviewCount - a.reviewCount);
      case 'reviews-desc':
        return list.sort((a, b) => b.reviewCount - a.reviewCount);
      default:
        return list;
    }
  });

  // ---------- 模板事件 ----------
  toggleSubject(value: string): void {
    this.subject.update((current) => (current === value ? '' : value));
  }

  toggleGradeBand(value: string): void {
    this.gradeBand.update((current) => (current === value ? '' : value));
  }

  toggleStyleTag(value: string): void {
    this.styleTag.update((current) => (current === value ? '' : value));
  }

  setPriceBand(value: number | null): void {
    this.maxPrice.set(value);
  }

  onIncludeUnverifiedChange(event: Event): void {
    this.includeUnverified.set((event.target as HTMLInputElement).checked);
  }

  clearFilters(): void {
    this.keywordInput.set('');
    this.subject.set('');
    this.gradeBand.set('');
    this.maxPrice.set(null);
    this.styleTag.set('');
    this.includeUnverified.set(false);
  }

  retry(): void {
    this.load(untracked(this.query));
  }

  private load(query: TeacherQuery): void {
    this.loading.set(true);
    this.error.set(false);
    this.teachersApi.search(query).subscribe({
      next: (list) => {
        this.results.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
