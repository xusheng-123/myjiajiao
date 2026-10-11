import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface SettlementEntry {
  entryId: string;
  bookingId: string;
  grossCents: number;
  platformFeeCents: number;
  netCents: number;
  status: string;
  settledAt: string | null;
  entryKind: 'EARNING' | 'REVERSAL';
  reversesEntryId: string | null;
}

export interface Settlement {
  pendingNetCents: number;
  paidOutNetCents: number;
  entries: SettlementEntry[];
}

@Component({
  selector: 'nur-settlement-card',
  standalone: true,
  imports: [],
  templateUrl: './settlement-card.component.html',
  styleUrl: './settlement-card.component.css',
})
export class SettlementCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly settlement = signal<Settlement | null>(null);
  readonly loading = signal(false);

  ngOnInit(): void {
    this.refreshSettlement();
  }

  refreshSettlement(): void {
    this.loading.set(true);
    this.http.get<Settlement>(API.teacher.settlement).subscribe({
      next: (d) => {
        this.settlement.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  yuan(cents: number): string {
    return (cents / 100).toFixed(2);
  }
}
