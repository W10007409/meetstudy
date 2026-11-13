import { plannerRows, type PlannerRow } from '../domain/report'
import { STATE_COLOR } from '../domain/states'
import type { Segment } from '../domain/types'
import './ui.css'

export const hour12 = (h: number) => (h % 12 === 0 ? 12 : h % 12)

/** Six 10-minute cells, each holding ten one-minute slivers coloured by state. */
export function PlannerCells({ row }: { row: PlannerRow }) {
  return (
    <>
      {[0, 1, 2, 3, 4, 5].map(c => (
        <div className="cell" key={c}>
          <div className="fill">
            {row.minutes.slice(c * 10, c * 10 + 10).map((s, k) => (
              <i key={k} data-minute={c * 10 + k} data-state={s ?? ''} style={{ background: s ? STATE_COLOR[s] : 'transparent' }} />
            ))}
          </div>
        </div>
      ))}
    </>
  )
}

/** The study-planner timetable: one row per hour of the session (Seoul time). */
export function Planner({ segments }: { segments: Segment[] }) {
  const rows = plannerRows(segments)
  return (
    <div>
      <div className="planner" role="table" aria-label="공부 시간표">
        <div className="m" />
        {[0, 10, 20, 30, 40, 50].map(n => <div className="m" key={n}>{n}</div>)}
        {rows.map(r => (
          <div className="row" data-testid="planner-row" key={r.startMs} role="row">
            <div className="h">{hour12(r.hour)}시</div>
            <PlannerCells row={r} />
          </div>
        ))}
      </div>
      <div className="legend">
        <span style={{ ['--c' as string]: STATE_COLOR.STUDYING }}>공부</span>
        <span style={{ ['--c' as string]: STATE_COLOR.LOOKING_AWAY }}>딴짓</span>
        <span style={{ ['--c' as string]: STATE_COLOR.AWAY }}>자리 비움</span>
        <span style={{ ['--c' as string]: STATE_COLOR.BREAK }}>휴식</span>
      </div>
    </div>
  )
}
