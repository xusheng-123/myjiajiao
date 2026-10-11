import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { AuthService } from '../../core/auth/auth.service';
import { ROLE_HOME } from '../../core/auth/auth.models';
import { StorageQuotaComponent } from '../../shared/components/storage-quota.component';

interface Conversation {
  conversationId: string;
  counterpartName: string;
  subject: string | null;
  unreadCount: number;
}

interface Message {
  messageId: string;
  body: string;
  sentAt: string;
  readAt: string | null;
  mine: boolean;
  attachmentId?: string | null;
}

@Component({
  selector: 'nur-messages',
  standalone: true,
  imports: [RouterLink, DatePipe, StorageQuotaComponent],
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.css'
})
export class MessagesComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  readonly home = computed(() => ROLE_HOME[this.auth.role()!]);

  readonly mobileView = signal<'list' | 'chat'>('list');
  readonly conversations = signal<Conversation[]>([]);
  readonly active = signal<Conversation | null>(null);
  readonly messages = signal<Message[]>([]);
  readonly quotaRevision = signal(0);
  readonly draft = signal('');
  readonly notice = signal('');
  readonly loaded = signal(false);
  readonly busy = signal(false);
  readonly sending = signal(false);
  readonly sendFailed = signal(false);
  readonly lastFailedDraft = signal('');
  readonly page = signal(0);
  readonly hasOlder = signal(false);

  private timer?: ReturnType<typeof setInterval>;
  private requestedId: string | null = null;
  private destroyed = false;
  private readonly onVisibilityChange = () => this.handleVisibilityChange();

  ngOnInit(): void {
    this.requestedId = this.route.snapshot.queryParamMap.get('conversation');
    this.refresh();
    this.startPolling();
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', this.onVisibilityChange);
    }
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.stopPolling();
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', this.onVisibilityChange);
    }
  }

  private startPolling(): void {
    this.stopPolling();
    this.timer = setInterval(() => {
      if (typeof document !== 'undefined' && !document.hidden && !this.busy()) {
        this.refresh();
      }
    }, 15000);
  }

  private stopPolling(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  private handleVisibilityChange(): void {
    if (typeof document === 'undefined') return;
    if (document.hidden) {
      this.stopPolling();
    } else {
      this.startPolling();
      this.refresh();
    }
  }

  backToList(): void {
    this.mobileView.set('list');
  }

  refresh(): void {
    this.http.get<Conversation[]>(API.messaging.conversations).subscribe({
      next: rows => {
        if (this.destroyed) return;
        this.loaded.set(true);
        this.conversations.set(rows);
        const current = rows.find(c => c.conversationId === (this.active()?.conversationId ?? this.requestedId));
        if (current && !this.active()) {
          this.select(current);
        } else if (current) {
          this.active.set(current);
          this.loadMessages(false);
        } else if (this.requestedId) {
          this.notice.set('未找到这段会话，请刷新或重新发起咨询。');
        }
      },
      error: e => {
        this.loaded.set(true);
        this.notice.set(e.error?.detail ?? '会话加载失败，请重试。');
      }
    });
  }

  select(c: Conversation): void {
    if (this.sending()) return;
    this.active.set(c);
    this.mobileView.set('chat');
    this.messages.set([]);
    this.page.set(0);
    this.notice.set('');
    this.sendFailed.set(false);
    this.lastFailedDraft.set('');

    // Restore cached draft from localStorage
    const cached = this.getStoredDraft(c.conversationId);
    this.draft.set(cached);

    this.loadMessages(false);
  }

  updateDraft(value: string): void {
    this.draft.set(value);
    const id = this.active()?.conversationId;
    if (!id || typeof localStorage === 'undefined') return;
    try {
      if (value.trim()) {
        localStorage.setItem(`nurture_msg_draft_${id}`, value);
      } else {
        localStorage.removeItem(`nurture_msg_draft_${id}`);
      }
    } catch {
      // quota or private browsing safety
    }
  }

  private getStoredDraft(id: string): string {
    if (typeof localStorage === 'undefined') return '';
    try {
      return localStorage.getItem(`nurture_msg_draft_${id}`) ?? '';
    } catch {
      return '';
    }
  }

  private clearStoredDraft(id: string): void {
    if (typeof localStorage === 'undefined') return;
    try {
      localStorage.removeItem(`nurture_msg_draft_${id}`);
    } catch {
      // safe
    }
  }

  loadMessages(older: boolean): void {
    const id = this.active()?.conversationId;
    if (!id || this.busy()) return;
    const page = older ? this.page() + 1 : 0;
    this.busy.set(true);

    this.http.get<Message[]>(API.messaging.messages(id), { params: { page } }).subscribe({
      next: rows => {
        this.busy.set(false);
        if (this.destroyed || id !== this.active()?.conversationId) return;
        const merged = new Map(this.messages().map(m => [m.messageId, m]));
        rows.forEach(m => merged.set(m.messageId, m));
        this.messages.set(
          [...merged.values()].sort((a, b) => a.sentAt.localeCompare(b.sentAt) || a.messageId.localeCompare(b.messageId))
        );
        if (older) this.page.set(page);
        if (older || this.page() === 0) this.hasOlder.set(rows.length === 100);

        // Only mark messages after the user has actually opened this conversation in a visible tab.
        if (typeof document !== 'undefined' && !document.hidden) {
          this.http.post(`${API.messaging.conversations}/${id}:read`, {}).subscribe({
            next: () => this.conversations.update(cs => cs.map(c => (c.conversationId === id ? { ...c, unreadCount: 0 } : c))),
            error: () => this.notice.set('消息已加载，但已读状态暂未更新。')
          });
        }
      },
      error: e => {
        this.busy.set(false);
        this.notice.set(e.error?.detail ?? '消息加载失败，请重试。');
      }
    });
  }

  upload(event: Event): void {
    const input = event.target as HTMLInputElement,
      file = input.files?.[0],
      id = this.active()?.conversationId;
    if (!file || !id || this.sending()) return;
    if (file.size > 10485760) {
      this.notice.set('咨询附件不能超过10MB。');
      input.value = '';
      return;
    }
    const form = new FormData();
    form.append('file', file);
    this.sending.set(true);

    this.http.post('/api/v1/messaging/conversations/' + id + '/files', form).subscribe({
      next: () => {
        this.sending.set(false);
        input.value = '';
        this.quotaRevision.update(x => x + 1);
        this.notice.set('附件已发送并与消息共同保留。');
        this.refresh();
      },
      error: e => {
        this.sending.set(false);
        input.value = '';
        this.notice.set(e.error?.detail ?? '上传失败，请重试。');
      }
    });
  }

  download(m: Message): void {
    if (!m.attachmentId || this.sending()) return;
    this.sending.set(true);

    this.http.get('/api/v1/messaging/files/' + m.attachmentId + '/download', { responseType: 'blob' }).subscribe({
      next: blob => {
        this.sending.set(false);
        const url = URL.createObjectURL(blob),
          a = document.createElement('a');
        a.href = url;
        a.download = m.body.replace(/^附件：/, '');
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
      },
      error: e => {
        this.sending.set(false);
        this.notice.set(e.error?.detail ?? '附件下载失败。');
      }
    });
  }

  send(): void {
    const id = this.active()?.conversationId,
      body = this.draft().trim();
    if (!id || !body || body.length > 4000 || this.sending()) return;
    this.sending.set(true);
    this.sendFailed.set(false);
    this.notice.set('');

    this.http.post<Message>(API.messaging.messages(id), { body }).subscribe({
      next: m => {
        this.sending.set(false);
        this.sendFailed.set(false);
        this.lastFailedDraft.set('');
        this.messages.update(rows => [...rows, m]);
        this.draft.set('');
        this.clearStoredDraft(id);
        this.refresh();
      },
      error: e => {
        this.sending.set(false);
        this.sendFailed.set(true);
        this.lastFailedDraft.set(body);
        this.notice.set(e.error?.detail ?? '发送失败，草稿已保留。');
      }
    });
  }

  retrySend(): void {
    if (this.lastFailedDraft()) {
      this.draft.set(this.lastFailedDraft());
    }
    this.send();
  }
}
