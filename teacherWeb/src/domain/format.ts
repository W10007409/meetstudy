// Korean date/time words for reports. Seoul time, fixed UTC+9 (see report.ts).
const OFFSET = 9 * 3_600_000
const DAYS = ['일', '월', '화', '수', '목', '금', '토']
const seoul = (ms: number) => new Date(ms + OFFSET)

/** "11월 13일 목요일" */
export function headerDate(ms: number): string {
  const d = seoul(ms)
  return `${d.getUTCMonth() + 1}월 ${d.getUTCDate()}일 ${DAYS[d.getUTCDay()]}요일`
}

const period = (ms: number) => (seoul(ms).getUTCHours() < 12 ? '오전' : '오후')

/** "2:10" on a 12-hour clock. */
export function clock12(ms: number): string {
  const d = seoul(ms)
  const h = d.getUTCHours() % 12 || 12
  return `${h}:${String(d.getUTCMinutes()).padStart(2, '0')}`
}

/** "오후 2:10부터 3:18까지"; the second 오전/오후 only when it changes. */
export function timeRange(startMs: number, endMs: number): string {
  const p2 = period(endMs) === period(startMs) ? '' : `${period(endMs)} `
  return `${period(startMs)} ${clock12(startMs)}부터 ${p2}${clock12(endMs)}까지`
}

/** "53분", "1시간 8분", "2시간" (whole minutes, rounded down). */
export function minutesText(ms: number): string {
  const m = Math.floor(ms / 60_000)
  if (m < 60) return `${m}분`
  return m % 60 === 0 ? `${m / 60}시간` : `${Math.floor(m / 60)}시간 ${m % 60}분`
}

/**
 * Sentence comparing with the previous session, or null when there is none. [good] colours it teal;
 * for lost time going down is good.
 */
export function comparisonText(curMs: number, prevMs: number | undefined, lowerIsBetter: boolean): { text: string; good: boolean } | null {
  if (prevMs === undefined) return null
  const diff = Math.round((curMs - prevMs) / 60_000)
  if (diff === 0) return { text: '지난번과 같아요', good: true }
  const up = diff > 0
  return { text: `지난번보다 ${Math.abs(diff)}분 ${up ? '늘었어요' : '줄었어요'}`, good: up !== lowerIsBetter }
}
