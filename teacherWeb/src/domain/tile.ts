import type { TileState } from '../ui/StatusBadge'
import { LOST_STATES } from './states'
import type { Presence } from './types'

export const OFFLINE_AFTER_MS = 60_000

/** What a room tile shows for a student right now. */
export function tileState(p: Presence | undefined, nowMs: number, breakMinutes: number): { state: TileState; detail?: string } {
  if (!p) return { state: 'waiting' }
  if (nowMs - p.updatedAtMs > OFFLINE_AFTER_MS) return { state: 'offline' }
  const forMs = Math.max(0, nowMs - p.since)
  if (p.state === 'BREAK') {
    const left = Math.ceil((breakMinutes * 60_000 - forMs) / 60_000)
    return { state: 'BREAK', detail: left > 0 ? `${left}분 남음` : '곧 끝나요' }
  }
  if (LOST_STATES.has(p.state)) {
    return { state: p.state, detail: forMs < 60_000 ? `${Math.floor(forMs / 1000)}초째` : `${Math.floor(forMs / 60_000)}분째` }
  }
  return { state: p.state }
}
