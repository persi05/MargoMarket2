import { Injectable, signal } from '@angular/core';

export interface AccountBlock {
  reason: string;
  expiresAt: string | null;
  message?: string;
}
@Injectable({ providedIn: 'root' })
export class AccountAccessService {
  readonly block = signal<AccountBlock | null>(null);
  clear() {
    this.block.set(null);
  }
  show(block: AccountBlock) {
    this.block.set({ reason: block.reason, expiresAt: block.expiresAt, message: block.message });
  }
}
