import { LOST_STATES } from './states'
import type { FocusState, Segment, SessionDoc } from './types'

// Same maths as shared/.../domain/Report.kt; contracts/report-cases.json keeps them in step.
// Korea has used a fixed UTC+9 with no daylight saving since 1988, so Seoul time is plain arithmetic.
const SEOUL_OFFSET_MS = 9 * 3_600_000
const HOUR_MS = 3_600_000
const DAY_MS = 24 * HOUR_MS

const dur = (s: Segment) => s.endMs - s.startMs
const sum = (s: Segment[], keep: (x: Segment) => boolean) => s.filter(keep).reduce((a, x) => a + dur(x), 0)

export const totalMs = (s: Segment[]) => sum(s, () => true)
export const focusedMs = (s: Segment[]) => sum(s, x => x.state === 'STUDYING')
export const lostMs = (s: Segment[]) => sum(s, x => LOST_STATES.has(x.state))
export const breakMs = (s: Segment[]) => sum(s, x => x.state === 'BREAK')

/** Focused share of the whole session, rounded half up. */
export function focusRatePct(s: Segment[]): number {
  const t = totalMs(s)
  return t === 0 ? 0 : Math.floor((focusedMs(s) * 200 + t) / (t * 2))
}

/** "YYYY-MM-DD" of an instant in Seoul. */
export const seoulYmd = (ms: number) => new Date(ms + SEOUL_OFFSET_MS).toISOString().slice(0, 10)
/** Hour of day (0-23) in Seoul. */
export const seoulHour = (ms: number) => new Date(ms + SEOUL_OFFSET_MS).getUTCHours()
/** Epoch ms at which the Seoul hour containing [ms] starts. */
export const seoulHourStart = (ms: number) => Math.floor((ms + SEOUL_OFFSET_MS) / HOUR_MS) * HOUR_MS - SEOUL_OFFSET_MS

export interface PlannerRow { hour: number; startMs: number; minutes: (FocusState | null)[] }

/** Study-planner rows; a minute takes the state covering its :30 mark. */
export function plannerRows(s: Segment[]): PlannerRow[] {
  if (s.length === 0) return []
  const first = seoulHourStart(Math.min(...s.map(x => x.startMs)))
  const last = seoulHourStart(Math.max(...s.map(x => x.endMs)) - 1)
  const rows: PlannerRow[] = []
  for (let h = first; h <= last; h += HOUR_MS) {
    rows.push({ hour: seoulHour(h), startMs: h, minutes: Array.from({ length: 60 }, (_, m) => stateAt(s, h + m * 60_000 + 30_000)) })
  }
  return rows
}

export const stateAt = (s: Segment[], t: number): FocusState | null =>
  s.find(x => t >= x.startMs && t < x.endMs)?.state ?? null

const stars = (r: SessionDoc) => (r.goalStar ? 1 : 0) + (r.proofStar ? 1 : 0)
const addDays = (ymd: string, n: number) => new Date(Date.parse(ymd + 'T00:00:00Z') + n * DAY_MS).toISOString().slice(0, 10)

/** One entry per day ending at [todayYmd], oldest first; a session counts on its Seoul start date. */
export function dailyTotals(records: SessionDoc[], todayYmd: string, days: number) {
  return Array.from({ length: days }, (_, i) => {
    const date = addDays(todayYmd, i - days + 1)
    const rs = records.filter(r => seoulYmd(r.startMs) === date)
    return { date, focusedMs: rs.reduce((a, r) => a + focusedMs(r.segments), 0), stars: rs.reduce((a, r) => a + stars(r), 0) }
  })
}

/** Consecutive goal days ending today, or ending yesterday when today has no goal yet. */
export function streakDays(records: SessionDoc[], todayYmd: string): number {
  const goal = new Set(records.filter(r => r.goalStar).map(r => seoulYmd(r.startMs)))
  let d = goal.has(todayYmd) ? todayYmd : addDays(todayYmd, -1)
  let n = 0
  while (goal.has(d)) { n++; d = addDays(d, -1) }
  return n
}

export function previousSession<T extends SessionDoc>(all: T[], r: T): T | undefined {
  return all.filter(x => x.startMs < r.startMs).sort((a, b) => b.startMs - a.startMs)[0]
}
