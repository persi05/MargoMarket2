import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { formatWait, rateLimitSeconds } from './rate-limit';

describe('rate limit responses', () => {
  it('reads the wait time from the body, then from the Retry-After header', () => {
    expect(rateLimitSeconds(new HttpErrorResponse({ status: 429, error: { retryAfterSeconds: 42 } }))).toBe(42);
    expect(rateLimitSeconds(new HttpErrorResponse({ status: 429, error: null, headers: new HttpHeaders({ 'Retry-After': '7' }) }))).toBe(7);
    expect(rateLimitSeconds(new HttpErrorResponse({ status: 429, error: 'oops' }))).toBe(1);
  });

  it('ignores other errors', () => {
    expect(rateLimitSeconds(new HttpErrorResponse({ status: 401, error: { retryAfterSeconds: 42 } }))).toBe(0);
    expect(rateLimitSeconds(new Error('network'))).toBe(0);
  });

  it('formats short and long waits', () => {
    expect(formatWait(45)).toBe('45 s');
    expect(formatWait(180)).toBe('3:00 min');
    expect(formatWait(61.2)).toBe('1:02 min');
  });
});
