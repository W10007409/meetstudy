import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router'
import { backend } from './backend'
import { BackendProvider, useBackend } from './backend/context'
import { useWatch } from './backend/hooks'
import type { RoomDoc, WithId } from './domain/types'
import { ReportPage } from './pages/ReportPage'
import { RoomPage } from './pages/RoomPage'
import { Shell } from './Shell'

/** "/" opens the first open room, or invites making one. */
function Home() {
  const b = useBackend()
  const rooms = useWatch<WithId<RoomDoc>[] | null>(cb => b.watchRooms(cb), null, [b])
  if (rooms === null) return null
  const open = rooms.find(r => r.status === 'open')
  if (open) return <Navigate to={`/rooms/${open.id}`} replace />
  return (
    <main style={{ padding: '48px 28px' }}>
      <h1 style={{ margin: 0, fontSize: 24 }}>열린 방이 없어요</h1>
      <p style={{ color: 'var(--ink-2)' }}>방을 만들고 학생을 초대하면 여기서 공부하는 모습을 볼 수 있어요.</p>
      <Link to="/rooms/new" style={{ color: 'var(--teal)', fontWeight: 600 }}>방 만들기</Link>
    </main>
  )
}

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Home />} />
        <Route path="/rooms/:id" element={<RoomPage />} />
        <Route path="/students/:uid/report/:sessionId?" element={<ReportPage />} />
      </Route>
    </Routes>
  )
}

export function App() {
  return (
    <BackendProvider backend={backend}>
      <BrowserRouter><AppRoutes /></BrowserRouter>
    </BackendProvider>
  )
}
