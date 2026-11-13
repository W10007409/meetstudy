import { NavLink } from 'react-router'
import { useBackend } from '../backend/context'
import { useWatch } from '../backend/hooks'
import type { Me } from '../backend/Backend'
import type { LinkDoc, RoomDoc, WithId } from '../domain/types'
import './shell.css'

const dateLabel = (ms: number) => {
  const d = new Date(ms + 9 * 3_600_000)
  const today = new Date(Date.now() + 9 * 3_600_000)
  const days = Math.round((Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()) - Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())) / 86_400_000)
  return days === 0 ? '오늘' : days === 1 ? '어제' : `${d.getUTCMonth() + 1}월 ${d.getUTCDate()}일`
}

export function Nav() {
  const b = useBackend()
  const me = useWatch<Me | null>(cb => b.watchMe(cb), null, [b])
  const rooms = useWatch<WithId<RoomDoc>[]>(cb => b.watchRooms(cb), [], [b])
  const links = useWatch<LinkDoc[]>(cb => b.watchLinks(cb), [], [b])
  const open = rooms.filter(r => r.status === 'open')
  const closed = rooms.filter(r => r.status === 'closed').sort((a, c) => (c.closedAt ?? 0) - (a.closedAt ?? 0))
  const linked = links.filter(l => l.status === 'accepted').length
  const waiting = links.filter(l => l.status === 'pending').length
  return (
    <nav aria-label="내 방" className="nav">
      {me && <p className="who"><b>{me.name}</b>연결된 학생 {linked}명</p>}
      <h2>열린 방</h2>
      {open.length === 0 && <p className="none">열린 방이 없어요</p>}
      {open.map(r => (
        <NavLink key={r.id} to={`/rooms/${r.id}`} className="item">
          <span>{r.name}</span><small>{r.studentUids.length}명</small>
        </NavLink>
      ))}
      <NavLink to="/rooms/new" className="item new" end>+ 방 만들기</NavLink>
      {closed.length > 0 && <h2>닫힌 방</h2>}
      {closed.slice(0, 5).map(r => (
        <NavLink key={r.id} to={`/rooms/${r.id}`} className="item closed">
          <span>{r.name}</span><small>{dateLabel(r.closedAt ?? r.openedAt)}</small>
        </NavLink>
      ))}
      <div className="spacer" />
      <NavLink to="/students" className="item" end>
        <span>학생 연결 관리</span>{waiting > 0 && <small>기다리는 요청 {waiting}</small>}
      </NavLink>
    </nav>
  )
}
