import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useBackend } from '../backend/context'
import { comparisonText, clock12, headerDate, minutesText, timeRange } from '../domain/format'
import { breakMs, dailyTotals, focusRatePct, focusedMs, lostMs, previousSession, seoulYmd, streakDays } from '../domain/report'
import { EVENT_LABEL, STATE_COLOR } from '../domain/states'
import type { SessionDoc, WithId } from '../domain/types'
import { FocusRing } from '../ui/FocusRing'
import { Planner } from '../ui/Planner'
import './report.css'

type Tab = 'session' | 'week' | 'month'
const DAY_MS = 86_400_000
const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토']

/** One student's report: this session, or the last 7 / 30 days. Same layout as the student app. */
export function ReportPage() {
  const { uid = '', sessionId } = useParams()
  const backend = useBackend()
  const [sessions, setSessions] = useState<WithId<SessionDoc>[] | null>(null)
  const [name, setName] = useState('')
  const [tab, setTab] = useState<Tab>('session')
  const [picked, setPicked] = useState<string | undefined>(sessionId)

  useEffect(() => {
    let live = true
    backend.listSessions(uid, Date.now() - 45 * DAY_MS).then(s => { if (live) setSessions(s) })
    const off = backend.watchLinks(ls => setName(ls.find(l => l.studentUid === uid)?.studentName ?? ''))
    return () => { live = false; off() }
  }, [backend, uid])
  useEffect(() => setPicked(sessionId), [sessionId])

  const today = seoulYmd(Date.now())
  if (sessions === null) return <main className="report"><p className="hint">불러오는 중이에요</p></main>
  if (sessions.length === 0) return <main className="report"><Header name={name} title="리포트" /><p className="empty-note">아직 공부한 기록이 없어요</p></main>

  const current = sessions.find(s => s.id === picked) ?? sessions.reduce((a, b) => (b.startMs > a.startMs ? b : a))
  return (
    <main className="report">
      <Header
        name={name}
        title={tab === 'session' ? `${headerDate(current.startMs)}의 공부` : tab === 'week' ? '이번 주' : '최근 30일'}
        sub={tab === 'session' ? timeRange(current.startMs, current.endMs) : undefined}
        tab={tab}
        onTab={setTab}
      />
      {tab === 'session'
        ? <SessionView s={current} all={sessions} today={today} />
        : <RangeView all={sessions} today={today} days={tab === 'week' ? 7 : 30} onPick={id => { setPicked(id); setTab('session') }} />}
    </main>
  )
}

function Header({ name, title, sub, tab, onTab }: { name: string; title: string; sub?: string; tab?: Tab; onTab?: (t: Tab) => void }) {
  return (
    <header className="top">
      <div>
        {name && <p className="who"><Link to="/students">{name} 학생</Link></p>}
        <h1>{title}</h1>
        {sub && <p className="sub-line">{sub}</p>}
      </div>
      {onTab && (
        <div className="tabs" role="tablist">
          {(['session', 'week', 'month'] as Tab[]).map(t => (
            <button key={t} role="tab" aria-selected={tab === t} onClick={() => onTab(t)}>
              {t === 'session' ? '이번 공부' : t === 'week' ? '주간' : '월간'}
            </button>
          ))}
        </div>
      )}
    </header>
  )
}

function SessionView({ s, all, today }: { s: WithId<SessionDoc>; all: WithId<SessionDoc>[]; today: string }) {
  const prev = previousSession(all, s)
  const focused = focusedMs(s.segments)
  const lost = lostMs(s.segments)
  const rest = breakMs(s.segments)
  const restCount = s.segments.filter(x => x.state === 'BREAK').length
  const stars = (s.goalStar ? 1 : 0) + (s.proofStar ? 1 : 0)
  const goalMin = Math.round(s.goalMs / 60_000)
  const left = s.goalMs - focused
  const events = s.segments.filter(x => x.state !== 'STUDYING')
  return (
    <>
      <section className="summary">
        <FocusRing pct={focusRatePct(s.segments)} />
        <div>
          <p className="headline">{minutesText(focused)} 동안 집중했어요</p>
          <p className="goal-line">
            {left <= 0 ? `목표 ${goalMin}분을 채웠어요` : `목표 ${goalMin}분까지 ${minutesText(left)} 남았어요`}
            {stars > 0 && <span className="stars" aria-label={`별 ${stars}개`}> {'★'.repeat(stars)}</span>}
          </p>
          <div className="nums">
            <Num k="순공부" v={minutesText(focused)} cmp={comparisonText(focused, prev && focusedMs(prev.segments), false)} />
            <Num k="흐트러진 시간" v={minutesText(lost)} cmp={comparisonText(lost, prev && lostMs(prev.segments), true)} />
            <Num k="쉬는 시간" v={minutesText(rest)} note={restCount ? `${restCount}번 쉬었어요` : '쉬지 않았어요'} />
          </div>
        </div>
      </section>
      <section className="main">
        <div className="sheet">
          <h2>공부 시간표</h2>
          <p className="hint">한 칸이 10분이에요. 색칠된 만큼 그 시간을 그렇게 보냈어요.</p>
          <Planner segments={s.segments} />
          {events.length === 0
            ? <p className="hint" style={{ marginTop: 16 }}>끝까지 한 번도 흐트러지지 않았어요</p>
            : (
              <ul className="log">
                {events.map(e => (
                  <li key={e.startMs}>
                    <time>{clock12(e.startMs)}</time>
                    <span className="c" style={{ background: STATE_COLOR[e.state] }} />
                    <span>{EVENT_LABEL[e.state as Exclude<typeof e.state, 'STUDYING'>]}</span>
                    <span className="dur">{minutesText(e.endMs - e.startMs) === '0분' ? '1분 미만' : minutesText(e.endMs - e.startMs)}</span>
                  </li>
                ))}
              </ul>
            )}
        </div>
        <aside className="side">
          <Week all={all} today={today} days={7} />
          <Proof path={s.proofPhotoPath} />
        </aside>
      </section>
    </>
  )
}

function Num({ k, v, cmp, note }: { k: string; v: string; cmp?: { text: string; good: boolean } | null; note?: string }) {
  return (
    <div className="num">
      <div className="k">{k}</div>
      <div className="val">{v}</div>
      {cmp && <div className={`d ${cmp.good ? 'good' : 'bad'}`}>{cmp.text}</div>}
      {note && <div className="d plain">{note}</div>}
    </div>
  )
}

function Week({ all, today, days, onPick }: { all: WithId<SessionDoc>[]; today: string; days: number; onPick?: (date: string) => void }) {
  const d = dailyTotals(all, today, days)
  const total = d.reduce((a, x) => a + x.focusedMs, 0)
  const max = Math.max(1, ...d.map(x => x.focusedMs))
  const goalDays = new Set(all.filter(s => s.goalStar).map(s => seoulYmd(s.startMs)))
  const streak = streakDays(all, today)
  const stars = d.reduce((a, x) => a + x.stars, 0)
  const label = (date: string, i: number) => {
    if (date === today) return '오늘'
    if (days === 7) return WEEKDAY[new Date(date + 'T00:00:00Z').getUTCDay()]
    return i % 5 === 0 ? String(Number(date.slice(8))) : ''
  }
  return (
    <div className="week">
      {days === 7 && <h2>이번 주</h2>}
      <div className="week-total">{minutesText(total)}</div>
      <p className="hint">하루 평균 {minutesText(total / days)} 공부했어요</p>
      <div className={`bars${days > 7 ? ' dense' : ''}`}>
        {d.map(x => (
          <button
            key={x.date}
            type="button"
            data-testid="bar"
            className={`bar${x.date === today ? ' today' : ''}${goalDays.has(x.date) ? ' goal' : ''}`}
            title={`${x.date} ${minutesText(x.focusedMs)}`}
            onClick={onPick && x.focusedMs > 0 ? () => onPick(x.date) : undefined}
            disabled={!onPick || x.focusedMs === 0}
          >
            <i style={{ height: `${Math.max(3, (x.focusedMs / max) * 86)}%` }} />
          </button>
        ))}
      </div>
      <div className={`days${days > 7 ? ' dense' : ''}`}>
        {d.map((x, i) => <span key={x.date} className={x.date === today ? 'today' : ''}>{label(x.date, i)}</span>)}
      </div>
      <p className="streak">
        {streak > 0 ? <><b>{streak}일 연속</b> 목표를 채웠어요. </> : null}
        {days === 7 ? '이번 주' : '이 기간'} 별은 {stars}개예요.
      </p>
    </div>
  )
}

function Proof({ path }: { path: string | null }) {
  const backend = useBackend()
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    let live = true
    setUrl(null)
    if (path) backend.proofPhotoUrl(path).then(u => { if (live) setUrl(u) })
    return () => { live = false }
  }, [backend, path])
  return (
    <div className="proof">
      {url ? <a href={url} target="_blank" rel="noreferrer"><img src={url} alt="인증한 문제집 사진" /></a> : <div className="img" aria-hidden="true" />}
      <div>
        <p>인증한 문제집</p>
        <small>{path ? (url ? '공부를 마치고 찍은 사진이에요' : '사진을 불러오는 중이에요') : '사진이 아직 없어요'}</small>
      </div>
    </div>
  )
}

function RangeView({ all, today, days, onPick }: { all: WithId<SessionDoc>[]; today: string; days: number; onPick: (id: string) => void }) {
  const from = useMemo(() => Date.parse(today + 'T00:00:00+09:00') - (days - 1) * DAY_MS, [today, days])
  const list = all.filter(s => s.startMs >= from).sort((a, b) => b.startMs - a.startMs)
  const pickDate = (date: string) => { const s = list.find(x => seoulYmd(x.startMs) === date); if (s) onPick(s.id) }
  return (
    <section className="range">
      <div className="sheet"><Week all={all} today={today} days={days} onPick={pickDate} /></div>
      <ul className="sessions">
        {list.map(s => (
          <li key={s.id}>
            <button type="button" onClick={() => onPick(s.id)}>
              <span className="date">{headerDate(s.startMs)}</span>
              <span>{timeRange(s.startMs, s.endMs)}</span>
              <span className="focus">{minutesText(focusedMs(s.segments))} 집중</span>
              {(s.goalStar || s.proofStar) && <span className="stars">{'★'.repeat((s.goalStar ? 1 : 0) + (s.proofStar ? 1 : 0))}</span>}
            </button>
          </li>
        ))}
        {list.length === 0 && <li className="hint">이 기간에는 공부한 기록이 없어요</li>}
      </ul>
    </section>
  )
}
