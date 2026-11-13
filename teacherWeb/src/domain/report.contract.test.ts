import { describe, expect, test } from 'vitest'
import contract from '../../../contracts/report-cases.json'
import type { FocusState, Segment, SessionDoc } from './types'
import { dailyTotals, focusRatePct, focusedMs, lostMs, plannerRows, previousSession, streakDays, totalMs } from './report'

// Same cases as shared/src/androidUnitTest/.../ReportContractTest.kt: both platforms must agree.
const CODE: Record<FocusState, string> = {
  STUDYING: 'S', AWAY: 'A', LOOKING_AWAY: 'L', CAMERA_GAZE: 'C', DROWSY: 'D', PHONE: 'P', APP_LEFT: 'X', BREAK: 'B',
}

describe('report contract', () => {
  for (const c of contract.cases) {
    test(c.name, () => {
      const s = c.segments as Segment[]
      expect(totalMs(s)).toBe(c.expect.totalMs)
      expect(focusedMs(s)).toBe(c.expect.focusedMs)
      expect(focusRatePct(s)).toBe(c.expect.focusRatePct)
      expect(lostMs(s)).toBe(c.expect.lostMs)
      expect(plannerRows(s).map(r => ({ hour: r.hour, row: r.minutes.map(m => (m ? CODE[m] : '.')).join('') }))).toEqual(c.expect.planner)
    })
  }
  for (const c of contract.days) {
    test(c.name, () => {
      const records = c.records.map(r => ({
        ...r, studentUid: 's', roomId: null, endMs: Math.max(...r.segments.map(s => s.endMs)), goalMs: 0, proofPhotoPath: null,
      })) as (SessionDoc & { id: string })[]
      expect(dailyTotals(records, c.today, c.days)).toEqual(c.expect.daily)
      expect(streakDays(records, c.today)).toBe(c.expect.streak)
    })
  }
  test('previousSessionIsTheLatestEarlierOne', () => {
    const r = (id: string, startMs: number) => ({ id, startMs }) as SessionDoc & { id: string }
    const all = [r('a', 100), r('b', 300), r('c', 200)]
    expect(previousSession(all, all[1])?.id).toBe('c')
    expect(previousSession(all, all[0])).toBeUndefined()
  })
})
