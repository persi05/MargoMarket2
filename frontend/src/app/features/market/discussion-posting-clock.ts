import { CommentPostingStatus } from '../../core/models/api.models';

export class DiscussionPostingClock {
  private cooldownDeadline = 0;
  private muteDeadline = 0;
  mutedUntil: string | null = null;

  constructor(private readonly monotonicNow: () => number = () => performance.now()) {}

  set(status: CommentPostingStatus): void {
    const now = this.monotonicNow();
    this.cooldownDeadline = now + Math.max(0, status.retryAfterSeconds) * 1000;
    this.muteDeadline = now + Math.max(0, status.muteRemainingSeconds) * 1000;
    this.mutedUntil = status.mutedUntil;
  }

  reset(): void {
    this.cooldownDeadline = 0;
    this.muteDeadline = 0;
    this.mutedUntil = null;
  }

  get retryAfterSeconds(): number {
    return Math.max(0, Math.ceil((this.cooldownDeadline - this.monotonicNow()) / 1000));
  }

  get muteRemainingSeconds(): number {
    return Math.max(0, Math.ceil((this.muteDeadline - this.monotonicNow()) / 1000));
  }
}

export function formatMuteDeadline(instant: string, timeZone?: string): string {
  return new Intl.DateTimeFormat('pl-PL', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    ...(timeZone ? { timeZone } : {})
  }).format(new Date(instant));
}
