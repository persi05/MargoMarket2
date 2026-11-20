import { afterEach, describe, expect, it, vi } from 'vitest';
import { DiscussionPostingClock, formatMuteDeadline } from './discussion-posting-clock';

const status = {
  retryAfterSeconds: 43200,
  muteRemainingSeconds: 43200,
  serverTime: '2026-09-27T16:00:00Z',
  mutedUntil: '2026-09-28T04:00:00Z'
};

describe('discussion posting clock', () => {
  afterEach(() => vi.restoreAllMocks());

  it('uses monotonic time even when the system clock jumps forwards or backwards', () => {
    let monotonicTime = 100;
    const clock = new DiscussionPostingClock(() => monotonicTime);
    clock.set(status);
    const systemClock = vi.spyOn(Date, 'now');
    systemClock.mockReturnValue(Date.parse('2099-01-01T00:00:00Z'));
    expect(clock.muteRemainingSeconds).toBe(43200);
    monotonicTime += 1000;
    expect(clock.muteRemainingSeconds).toBe(43199);
    systemClock.mockReturnValue(Date.parse('2000-01-01T00:00:00Z'));
    expect(clock.muteRemainingSeconds).toBe(43199);
  });

  it.each([
    ['UTC', '04:00:00'],
    ['Europe/Warsaw', '06:00:00'],
    ['America/New_York', '00:00:00'],
    ['Asia/Tokyo', '13:00:00']
  ])('shows the same mute deadline in %s', (zone, expected) => {
    expect(formatMuteDeadline(status.mutedUntil, zone)).toContain(expected);
  });

  it('expires both countdowns and accepts new server status', () => {
    let monotonicTime = 100;
    const clock = new DiscussionPostingClock(() => monotonicTime);
    clock.set(status);
    monotonicTime += 43200 * 1000;
    expect(clock.retryAfterSeconds).toBe(0);
    expect(clock.muteRemainingSeconds).toBe(0);
    clock.set({...status, retryAfterSeconds:600, muteRemainingSeconds:0, mutedUntil:null});
    expect(clock.retryAfterSeconds).toBe(600);
    expect(clock.muteRemainingSeconds).toBe(0);
    expect(clock.mutedUntil).toBeNull();
    clock.reset();
    expect(clock.retryAfterSeconds).toBe(0);
    expect(clock.muteRemainingSeconds).toBe(0);
  });
});
