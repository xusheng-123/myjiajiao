import { Routes } from '@angular/router';
import { Role } from './core/auth/auth.models';
import { roleGuard, sessionGuard, refundGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: 'messages', canActivate: [sessionGuard], loadComponent: () => import('./features/messages/messages.component').then(m => m.MessagesComponent) },
  { path: 'refunds', canActivate: [refundGuard], loadComponent: () => import('./features/refunds/refunds.component').then(m => m.RefundsComponent) },
  { path: '', loadComponent: () => import('./features/landing/landing.component').then((m) => m.LandingComponent) },
  { path: 'teachers', loadComponent: () => import('./features/teachers/teachers.component').then((m) => m.TeachersComponent) },
  { path: 'teachers/:teacherId', loadComponent: () => import('./features/teachers/teacher-detail.component').then(m => m.TeacherDetailComponent) },
  { path: 'auth', loadComponent: () => import('./features/auth/auth-portal.component').then((m) => m.AuthPortalComponent) },
  {
    path: 'student',
    canActivate: [roleGuard(Role.STUDENT)],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./features/student/student-dashboard.component').then((m) => m.StudentDashboardComponent) },
      { path: 'match', loadComponent: () => import('./features/match/match.component').then((m) => m.MatchComponent) },
      { path: 'growth', loadComponent: () => import('./features/student/student-growth.component').then((m) => m.StudentGrowthComponent) },
      { path: 'archive', loadComponent: () => import('./features/student/student-archive.component').then((m) => m.StudentArchiveComponent) },
      { path: 'questions', loadComponent: () => import('./features/student/student-questions.component').then((m) => m.StudentQuestionsComponent) },
    ],
  },
  {
    path: 'parent',
    canActivate: [roleGuard(Role.PARENT)],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./features/parent/parent-dashboard.component').then((m) => m.ParentDashboardComponent) },
    ],
  },
  {
    path: 'teacher',
    canActivate: [roleGuard(Role.TEACHER)],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./features/teacher/teacher-dashboard.component').then((m) => m.TeacherDashboardComponent) },
    ],
  },
  {
    path: 'admin',
    canActivate: [roleGuard(Role.ADMIN)],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./features/admin/admin-dashboard.component').then((m) => m.AdminDashboardComponent) },
    ],
  },
  { path: '**', redirectTo: '' },
];
