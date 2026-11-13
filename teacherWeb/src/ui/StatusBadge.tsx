import { OFFLINE_COLOR, STATE_COLOR, STATE_LABEL } from '../domain/states'
import type { FocusState } from '../domain/types'
import './ui.css'

export type TileState = FocusState | 'offline' | 'waiting'

export function StatusBadge({ state, detail }: { state: TileState; detail?: string }) {
  const label = state === 'offline' ? '오프라인' : state === 'waiting' ? '아직 안 들어왔어요' : STATE_LABEL[state]
  const color = state === 'offline' ? OFFLINE_COLOR : state === 'waiting' ? '#9AA5B1' : STATE_COLOR[state]
  return <span className="badge" style={{ background: color }}>{detail ? `${label} · ${detail}` : label}</span>
}
