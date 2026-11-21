import { HttpErrorResponse } from '@angular/common/http';

/** Seconds to wait from a 429 response (body first, then Retry-After header); 0 when not rate limited. */
export function rateLimitSeconds(response: unknown): number {
  if (!(response instanceof HttpErrorResponse) || response.status !== 429) return 0;
  const fromBody = Number((response.error as { retryAfterSeconds?: unknown } | null)?.retryAfterSeconds);
  const fromHeader = Number(response.headers?.get('Retry-After'));
  const seconds = Number.isFinite(fromBody) && fromBody > 0 ? fromBody : fromHeader;
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : 1;
}

export function formatWait(seconds: number): string {
  const total = Math.max(0, Math.ceil(seconds));
  if (total < 60) return `${total} s`;
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')} min`;
}
