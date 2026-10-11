import { Component, inject, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';
import { TeacherProfileEditorComponent } from '../../shared/components/teacher-profile-editor.component';
import { TodayLessonsCardComponent } from './today-lessons-card.component';
import { BookingRequestsCardComponent } from './booking-requests-card.component';
import { AvailabilityCardComponent } from './availability-card.component';
import { SubjectsPricingCardComponent } from './subjects-pricing-card.component';
import { SettlementCardComponent } from './settlement-card.component';
import { VerificationCardComponent } from './verification-card.component';
import { MyStudentsCardComponent } from './my-students-card.component';
import { StudentReviewsCardComponent } from './student-reviews-card.component';
import { LessonRecordsCardComponent } from './lesson-records-card.component';
import { OpenDemandsCardComponent } from './open-demands-card.component';
import { signal } from '@angular/core';

export type DashboardTab = 'all' | 'teaching' | 'business';

/**
 * 导师工作台（方向 D 黛绿暖金精简编排组件）
 * 拆分为 8 个独立业务卡片，主组件仅负责编排、导航与卡片间事件协同。
 */
@Component({
  selector: 'nur-teacher-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    NoticeCenterComponent,
    TeacherProfileEditorComponent,
    TodayLessonsCardComponent,
    BookingRequestsCardComponent,
    AvailabilityCardComponent,
    SubjectsPricingCardComponent,
    SettlementCardComponent,
    VerificationCardComponent,
    MyStudentsCardComponent,
    StudentReviewsCardComponent,
    LessonRecordsCardComponent,
    OpenDemandsCardComponent,
  ],
  templateUrl: './teacher-dashboard.component.html',
  styleUrl: './teacher-dashboard.component.css',
})
export class TeacherDashboardComponent {
  readonly auth = inject(AuthService);
  readonly displayName = this.auth.displayName;

  // 视图分区选项卡（降低 ADHD 认知负荷）
  readonly activeTab = signal<DashboardTab>('all');

  // 子卡片引用（用于跨卡片联动：如结课后直达录入记录）
  readonly lessonRecordsCard = viewChild<LessonRecordsCardComponent>('lessonRecordsCard');

  switchTab(tab: DashboardTab): void {
    this.activeTab.set(tab);
  }

  onRecordLesson(event: { bookingId: string; subject: string }): void {
    // 切换到教学 tab 以确保课堂记录卡片可见
    if (this.activeTab() === 'business') {
      this.activeTab.set('teaching');
    }
    const card = this.lessonRecordsCard();
    if (card) {
      card.openForBooking(event.bookingId, event.subject);
      // 滚动到记录卡片区域
      const el = document.getElementById('lesson-records-section');
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  }
}
