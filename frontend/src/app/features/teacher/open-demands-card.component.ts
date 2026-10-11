import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface OpenDemand {
  demandId: string;
  subject: string;
  gradeBand: string | null;
  note: string | null;
  budgetMaxCents: number | null;
  preferredTime: string | null;
}

@Component({
  selector: 'nur-open-demands-card',
  standalone: true,
  imports: [],
  templateUrl: './open-demands-card.component.html',
  styleUrl: './open-demands-card.component.css',
})
export class OpenDemandsCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly openDemands = signal<OpenDemand[]>([]);
  readonly loading = signal(false);

  ngOnInit(): void {
    this.loadDemands();
  }

  loadDemands(): void {
    this.loading.set(true);
    this.http.get<{ content: OpenDemand[] }>(`${API.matching.openDemands}?page=0&size=5`).subscribe({
      next: (data) => {
        this.openDemands.set(data?.content ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
