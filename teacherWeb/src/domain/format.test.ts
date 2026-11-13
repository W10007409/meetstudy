import { expect, test } from 'vitest'
import { clock12, comparisonText, headerDate, minutesText, timeRange } from './format'

const kst = (iso: string) => Date.parse(iso + '+09:00')

test('headerDate', () => expect(headerDate(kst('2026-11-13T14:10:00'))).toBe('11월 13일 금요일'))
test('timeRangeSamePeriod', () => expect(timeRange(kst('2026-11-13T14:10:00'), kst('2026-11-13T15:18:00'))).toBe('오후 2:10부터 3:18까지'))
test('timeRangeAcrossMidnight', () => expect(timeRange(kst('2026-11-13T23:50:00'), kst('2026-11-14T00:20:00'))).toBe('오후 11:50부터 오전 12:20까지'))
test('clock12', () => expect(clock12(kst('2026-11-13T09:05:00'))).toBe('9:05'))
test('minutesText', () => {
  expect(minutesText(53 * 60_000)).toBe('53분')
  expect(minutesText(68 * 60_000)).toBe('1시간 8분')
  expect(minutesText(120 * 60_000)).toBe('2시간')
  expect(minutesText(20_000)).toBe('0분')
})
test('comparisonMore', () => expect(comparisonText(53 * 60_000, 41 * 60_000, false)).toEqual({ text: '지난번보다 12분 늘었어요', good: true }))
test('comparisonLostDown', () => expect(comparisonText(7 * 60_000, 9 * 60_000, true)).toEqual({ text: '지난번보다 2분 줄었어요', good: true }))
test('comparisonLostUp', () => expect(comparisonText(9 * 60_000, 7 * 60_000, true)).toEqual({ text: '지난번보다 2분 늘었어요', good: false }))
test('comparisonSame', () => expect(comparisonText(10 * 60_000, 10 * 60_000 + 20_000, false)).toEqual({ text: '지난번과 같아요', good: true }))
test('comparisonNoPrevious', () => expect(comparisonText(10 * 60_000, undefined, false)).toBeNull())
