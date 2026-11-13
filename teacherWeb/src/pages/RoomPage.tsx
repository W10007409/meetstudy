import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { MAX_ROOM_STUDENTS } from '../backend/Backend'
import { useBackend } from '../backend/context'
import { useNow, useWatch } from '../backend/hooks'
import { minutesText } from '../domain/format'
import { LOST_STATES } from '../domain/states'
import { tileState } from '../domain/tile'
import type { FocusState, LinkDoc, Presence, RoomDoc, WithId } from '../domain/types'
import { PlannerStrip } from '../ui/PlannerStrip'
import { StatusBadge, type TileState } from '../ui/StatusBadge'
import './room.css'

const isLost = (s: TileState) => LOST_STATES.has(s as FocusState)

/** Live view of one room: who is studying right now. Watch only; the teacher never interrupts. */
export function RoomPage() {
  const { id = '' } = useParams()
  const b = useBackend()
  const nav = useNavigate()
  const now = useNow()
  const rooms = useWatch<WithId<RoomDoc>[] | null>(cb => b.watchRooms(cb), null, [b])
  const presence = useWatch<Record<string, Presence>>(cb => b.watchPresence(id, cb), {}, [b, id])
  const links = useWatch<LinkDoc[]>(cb => b.watchLinks(cb), [], [b])
  const [confirmClose, setConfirmClose] = useState(false)

  if (rooms === null) return <main className="room"><p className="muted">불러오는 중이에요</p></main>
  const room = rooms.find(r => r.id === id)
  if (!room) return <main className="room"><h1>방을 찾을 수 없어요</h1><p className="muted"><Link to="/">처음 화면으로 가기</Link></p></main>
  const nameOf = (uid: string) => links.find(l => l.studentUid === uid)?.studentName ?? '학생'

  if (room.status === 'closed') {
    return (
      <main className="room">
        <div className="head"><div><h1>{room.name}</h1><p>닫힌 방이에요</p></div></div>
        <h2 className="sub">참여했던 학생</h2>
        <ul className="joined">
          {room.studentUids.map(uid => <li key={uid}><Link to={`/students/${uid}/report`}>{nameOf(uid)} 리포트 보기</Link></li>)}
        </ul>
      </main>
    )
  }

  const tiles = room.studentUids.map(uid => ({ uid, p: presence[uid], t: tileState(presence[uid], now, room.breakLimit.minutes) }))
  const count = (pred: (s: TileState) => boolean) => tiles.filter(x => pred(x.t.state)).length
  const pulse: [number, string][] = [
    [count(s => s === 'STUDYING'), '명 공부 중'],
    [count(s => isLost(s) && s !== 'AWAY'), '명 딴짓'],
    [count(s => s === 'AWAY'), '명 자리 비움'],
    [count(s => s === 'BREAK'), '명 휴식'],
    [count(s => s === 'offline'), '명 오프라인'],
  ]
  const free = MAX_ROOM_STUDENTS - room.studentUids.length
  const breaks = room.breakLimit.count > 0 ? `휴식은 ${room.breakLimit.count}번까지 ${room.breakLimit.minutes}분씩이에요` : '휴식은 없어요'
  const toReport = (uid: string) => nav(`/students/${uid}/report`)

  return (
    <main className="room">
      <div className="head">
        <div>
          <h1>{room.name}</h1>
          <p>{minutesText(now - room.openedAt)}째 열려 있어요. 자리 비움은 {room.awayThresholdSec}초, {breaks}.</p>
        </div>
        <div className="actions">
          <button className="primary" onClick={() => nav(`/rooms/${room.id}/settings`)}>학생 초대</button>
          <button onClick={() => nav(`/rooms/${room.id}/settings`)}>방 설정</button>
          <button className="danger" onClick={() => setConfirmClose(true)}>방 닫기</button>
        </div>
      </div>

      <p className="pulse" data-testid="pulse">
        {pulse.filter(([n]) => n > 0).map(([n, label]) => <span key={label}><b>{n}</b>{label}</span>)}
      </p>

      <div className="grid">
        {tiles.map(({ uid, p, t }) => (
          <article
            key={uid}
            data-testid="tile"
            className={`tile${t.state === 'AWAY' ? ' away' : isLost(t.state) ? ' off' : ''}${t.state === 'offline' ? ' offline' : ''}`}
            onClick={() => toReport(uid)}
            onKeyDown={e => { if (e.key === 'Enter') toReport(uid) }}
            role="link"
            tabIndex={0}
          >
            <div className="video">{t.state === 'offline' ? '연결이 끊겼어요' : '영상은 3단계에서 연결돼요'}</div>
            <div className="body">
              <div className="name"><b>{nameOf(uid)}</b><StatusBadge state={t.state} detail={t.detail} /></div>
              {p ? (
                <>
                  <div className="time"><b>{minutesText(p.focusedSec * 1000)}</b> 공부했어요, 목표 {minutesText(p.goalSec * 1000)}</div>
                  <PlannerStrip segments={p.segments} nowMs={now} />
                </>
              ) : <div className="time">방에 들어오면 여기에 보여요</div>}
            </div>
          </article>
        ))}
        {free > 0 && (
          <button className="empty" onClick={() => nav(`/rooms/${room.id}/settings`)}>+ 학생 초대하기 ({free}자리 남음)</button>
        )}
      </div>

      {confirmClose && (
        <div className="scrim" onClick={() => setConfirmClose(false)}>
          <div role="dialog" aria-modal="true" aria-labelledby="close-title" className="dialog" onClick={e => e.stopPropagation()}>
            <h2 id="close-title">방을 닫을까요?</h2>
            <p>공부 중인 학생은 혼자 공부로 이어서 해요.</p>
            <div className="actions">
              <button onClick={() => setConfirmClose(false)}>계속 열어 두기</button>
              <button className="danger-fill" onClick={async () => { setConfirmClose(false); await b.closeRoom(room.id) }}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
