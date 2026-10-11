import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface LessonRecordView {
  recordId: string;
  bookingId: string;
  subject: string;
  knowledgePoints: string;
  performance: string;
  problems: string;
  nextSuggestion: string;
  note: string;
  createdAt: string;
}

@Component({
  selector: 'nur-lesson-records-card',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './lesson-records-card.component.html',
  styleUrl: './lesson-records-card.component.css',
})
export class LessonRecordsCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly records = signal<LessonRecordView[]>([]);
  readonly loading = signal(false);

  // 表单状态
  readonly showForm = signal(false);
  readonly formBusy = signal(false);
  readonly formNotice = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);

  // 表单输入项
  readonly inputBookingId = signal('');
  readonly inputSubject = signal('');
  readonly inputKnowledgePoints = signal('');
  readonly inputPerformance = signal('');
  readonly inputProblems = signal('');
  readonly inputNextSuggestion = signal('');
  readonly inputNote = signal('');

  ngOnInit(): void {
    this.loadRecords();
  }

  loadRecords(): void {
    this.loading.set(true);
    this.http.get<LessonRecordView[]>(API.learning.myRecords).subscribe({
      next: (data) => {
        this.records.set(data ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  openForBooking(bookingId: string, subject: string): void {
    this.inputBookingId.set(bookingId);
    this.inputSubject.set(subject);
    this.inputKnowledgePoints.set('');
    this.inputPerformance.set('');
    this.inputProblems.set('');
    this.inputNextSuggestion.set('');
    this.inputNote.set('');
    this.formNotice.set(null);
    this.showForm.set(true);
  }

  openManual(): void {
    this.inputBookingId.set('');
    this.inputSubject.set('');
    this.inputKnowledgePoints.set('');
    this.inputPerformance.set('');
    this.inputProblems.set('');
    this.inputNextSuggestion.set('');
    this.inputNote.set('');
    this.formNotice.set(null);
    this.showForm.set(true);
  }

  closeForm(): void {
    this.showForm.set(false);
    this.formNotice.set(null);
  }

  submitRecord(): void {
    const bookingId = this.inputBookingId().trim();
    const subject = this.inputSubject().trim();

    if (!bookingId) {
      this.formNotice.set({ type: 'error', text: '请填写关联课程预约ID（UUID）。' });
      return;
    }
    if (!subject) {
      this.formNotice.set({ type: 'error', text: '请填写教学科目。' });
      return;
    }

    if (this.formBusy()) return;
    this.formBusy.set(true);
    this.formNotice.set(null);

    const body = {
      bookingId,
      subject,
      knowledgePoints: this.inputKnowledgePoints().trim(),
      performance: this.inputPerformance().trim(),
      problems: this.inputProblems().trim(),
      nextSuggestion: this.inputNextSuggestion().trim(),
      note: this.inputNote().trim(),
    };

    this.http.post(API.learning.createRecord, body).subscribe({
      next: () => {
        this.formBusy.set(false);
        this.formNotice.set({ type: 'success', text: '课堂记录已成功录入，并同步至学生端学习档案！' });
        this.loadRecords();
        setTimeout(() => {
          this.closeForm();
        }, 1500);
      },
      error: (err) => {
        this.formBusy.set(false);
        this.formNotice.set({
          type: 'error',
          text: err?.error?.detail ?? '录入失败，该课程可能已有记录或参数不合法。',
        });
      },
    });
  }
}
