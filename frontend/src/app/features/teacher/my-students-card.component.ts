import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface TeacherStudent {
  studentId: string;
  displayName: string;
}

export interface StudentArchive {
  profile: {
    goal: string;
    stage: string;
    targetSubjects: string;
  };
  stats: {
    completedLessons: number;
    totalLessons: number;
    taskCompleted: number;
    taskTotal: number;
    lessonRecords: number;
  };
  lessonRecords: {
    recordId: string;
    bookingId: string;
    subject: string;
    knowledgePoints: string;
    performance: string;
    problems: string;
    nextSuggestion: string;
    note: string;
    createdAt: string;
  }[];
  history: {
    bookingId: string;
    subject: string;
    status: string;
    teacherName: string;
  }[];
}

@Component({
  selector: 'nur-my-students-card',
  standalone: true,
  imports: [],
  templateUrl: './my-students-card.component.html',
  styleUrl: './my-students-card.component.css',
})
export class MyStudentsCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly students = signal<TeacherStudent[]>([]);
  readonly loading = signal(false);

  readonly selectedStudent = signal<TeacherStudent | null>(null);
  readonly archiveData = signal<StudentArchive | null>(null);
  readonly archiveLoading = signal(false);
  readonly archiveError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadStudents();
  }

  loadStudents(): void {
    this.loading.set(true);
    this.http.get<TeacherStudent[]>(API.learning.myStudents).subscribe({
      next: (data) => {
        this.students.set(data ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  viewArchive(student: TeacherStudent): void {
    this.selectedStudent.set(student);
    this.archiveLoading.set(true);
    this.archiveError.set(null);
    this.archiveData.set(null);

    this.http.get<StudentArchive>(API.learning.studentArchive(student.studentId)).subscribe({
      next: (archive) => {
        this.archiveData.set(archive);
        this.archiveLoading.set(false);
      },
      error: (err) => {
        this.archiveLoading.set(false);
        this.archiveError.set(err?.error?.detail ?? '获取学生档案失败，请确认授课关系。');
      },
    });
  }

  closeArchive(): void {
    this.selectedStudent.set(null);
    this.archiveData.set(null);
  }
}
