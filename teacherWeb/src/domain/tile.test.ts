import { expect, test } from 'vitest'
import { tileState } from './tile'
import type { FocusState, Presence } from './types'

const now = 1_000_000_000
const p = (state: FocusState, sinceAgoMs: number, updatedAgoMs = 1_000): Presence => ({
  state, since: now - sinceAgoMs, focusedSec: 0, goalSec: 3600, sessionStartMs: now - 3_600_000, segments: [], updatedAtMs: now - updatedAgoMs,
})

test('offlineAfter60s', () => {
  expect(tileState(p('STUDYING', 5_000, 61_000), now, 10)).toEqual({ state: 'offline' })
  expect(tileState(p('STUDYING', 5_000, 59_000), now, 10)).toEqual({ state: 'STUDYING' })
})
test('waitingWhenNoPresence', () => expect(tileState(undefined, now, 10)).toEqual({ state: 'waiting' }))
test('distractedDetailSeconds', () => expect(tileState(p('LOOKING_AWAY', 12_000), now, 10)).toEqual({ state: 'LOOKING_AWAY', detail: '12초째' }))
test('distractedDetailMinutes', () => expect(tileState(p('AWAY', 185_000), now, 10)).toEqual({ state: 'AWAY', detail: '3분째' }))
test('breakRemaining', () => {
  expect(tileState(p('BREAK', 6 * 60_000 + 10_000), now, 10)).toEqual({ state: 'BREAK', detail: '4분 남음' })
  expect(tileState(p('BREAK', 12 * 60_000), now, 10)).toEqual({ state: 'BREAK', detail: '곧 끝나요' })
})
