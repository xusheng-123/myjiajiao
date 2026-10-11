import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface VerificationStatus {
  status: string;
  decisionNote: string | null;
  submittedAt: string | null;
  decidedAt: string | null;
  guidance: string;
}

export interface PickedFile {
  file: File;
  name: string;
  sizeLabel: string;
}

@Component({
  selector: 'nur-verification-card',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './verification-card.component.html',
  styleUrl: './verification-card.component.css',
})
export class VerificationCardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private static readonly MAX_FILE_BYTES = 20 * 1024 * 1024;

  readonly verification = signal<VerificationStatus | null>(null);
  readonly loaded = signal(false);

  readonly idDocumentFile = signal<PickedFile | null>(null);
  readonly degreeCertFile = signal<PickedFile | null>(null);
  readonly verificationBusy = signal(false);
  readonly verificationMessage = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);

  readonly canSubmitVerification = computed(() =>
    this.idDocumentFile() !== null && this.degreeCertFile() !== null && !this.verificationBusy());

  readonly showVerificationForm = computed(() => {
    if (!this.loaded()) return false;
    const status = this.verification()?.status;
    return status === undefined || status === 'NOT_SUBMITTED' || status === 'REJECTED';
  });

  ngOnInit(): void {
    this.loadStatus();
  }

  loadStatus(): void {
    this.http.get<VerificationStatus>(API.teacher.verificationStatus).subscribe({
      next: (data) => {
        this.verification.set(data);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }

  onVerificationFilePick(part: 'idDocument' | 'degreeCert', event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) return;

    if (file.size > VerificationCardComponent.MAX_FILE_BYTES) {
      this.verificationMessage.set({ type: 'error', text: `「${file.name}」超过 20MB 限制，请压缩后重新选择。` });
      input.value = '';
      return;
    }

    this.verificationMessage.set(null);
    const picked: PickedFile = { file, name: file.name, sizeLabel: formatBytes(file.size) };
    if (part === 'idDocument') {
      this.idDocumentFile.set(picked);
    } else {
      this.degreeCertFile.set(picked);
    }
    input.value = '';
  }

  removeVerificationFile(part: 'idDocument' | 'degreeCert'): void {
    if (part === 'idDocument') {
      this.idDocumentFile.set(null);
    } else {
      this.degreeCertFile.set(null);
    }
  }

  submitVerification(): void {
    const idDoc = this.idDocumentFile();
    const degree = this.degreeCertFile();
    if (!idDoc || !degree || this.verificationBusy()) return;

    const form = new FormData();
    form.append('idDocument', idDoc.file, idDoc.name);
    form.append('degreeCert', degree.file, degree.name);

    this.verificationBusy.set(true);
    this.verificationMessage.set(null);

    this.http.post<VerificationStatus>(API.teacher.verificationSubmit, form).subscribe({
      next: (v) => {
        this.verification.set(v);
        this.idDocumentFile.set(null);
        this.degreeCertFile.set(null);
        this.verificationBusy.set(false);
        this.verificationMessage.set({ type: 'success', text: '材料已提交，以下为服务端返回的最新审核状态。' });
      },
      error: (err) => {
        this.verificationBusy.set(false);
        const detail = err?.error?.detail;
        this.verificationMessage.set({
          type: 'error',
          text: detail ?? '提交未成功，请确认两个材料文件都已选择后重试；多次失败请联系运营。',
        });
      },
    });
  }
}

function formatBytes(size: number): string {
  if (size >= 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${Math.round(size / 1024)} KB`;
  }
  return `${size} B`;
}
