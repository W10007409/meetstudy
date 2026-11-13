import { seoulHour, seoulHourStart, stateAt } from '../domain/report'
import type { Segment } from '../domain/types'
import { PlannerCells, hour12 } from './Planner'
import './ui.css'

/** The current hour of a student's planner, small, for a room tile. */
export function PlannerStrip({ segments, nowMs }: { segments: Segment[]; nowMs: number }) {
  const start = seoulHourStart(nowMs)
  const row = { hour: seoulHour(start), startMs: start, minutes: Array.from({ length: 60 }, (_, m) => stateAt(segments, start + m * 60_000 + 30_000)) }
  const h = hour12(row.hour)
  return (
    <div className="strip">
      <div className="row" data-testid="planner-row"><PlannerCells row={row} /></div>
      <div className="strip-label"><span>{h}시</span><span>{h}시 50분</span></div>
    </div>
  )
}
