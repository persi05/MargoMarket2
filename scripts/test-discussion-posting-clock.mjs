import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';

const require = createRequire(new URL('../frontend/package.json', import.meta.url));
const ts = require('typescript');
const source = await readFile(new URL('../frontend/src/app/features/market/discussion-posting-clock.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
});
const { DiscussionPostingClock, formatMuteDeadline } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

let monotonicTime = 100;
const clock = new DiscussionPostingClock(() => monotonicTime);
const status = {
  retryAfterSeconds: 43200,
  muteRemainingSeconds: 43200,
  serverTime: '2026-09-27T16:00:00Z',
  mutedUntil: '2026-09-28T04:00:00Z'
};
clock.set(status);
assert.equal(clock.muteRemainingSeconds, 43200);
const originalDateNow = Date.now;
try {
  Date.now = () => Date.parse('2099-01-01T00:00:00Z');
  assert.equal(clock.muteRemainingSeconds, 43200);
  monotonicTime += 1000;
  assert.equal(clock.muteRemainingSeconds, 43199);
  Date.now = () => Date.parse('2000-01-01T00:00:00Z');
  assert.equal(clock.muteRemainingSeconds, 43199);
} finally {
  Date.now = originalDateNow;
}

assert.match(formatMuteDeadline(status.mutedUntil, 'UTC'), /04:00:00/);
assert.match(formatMuteDeadline(status.mutedUntil, 'Europe/Warsaw'), /06:00:00/);
assert.match(formatMuteDeadline(status.mutedUntil, 'America/New_York'), /00:00:00/);
assert.match(formatMuteDeadline(status.mutedUntil, 'Asia/Tokyo'), /13:00:00/);

monotonicTime += 43200 * 1000;
assert.equal(clock.retryAfterSeconds, 0);
assert.equal(clock.muteRemainingSeconds, 0);
clock.set({ ...status, retryAfterSeconds: 600, muteRemainingSeconds: 0, mutedUntil: null });
assert.equal(clock.retryAfterSeconds, 600);
assert.equal(clock.muteRemainingSeconds, 0);
assert.equal(clock.mutedUntil, null);
clock.reset();
assert.equal(clock.retryAfterSeconds, 0);
console.log('PASS: system clock jumps, monotonic countdown, four time zones, expiry and server resynchronization.');
