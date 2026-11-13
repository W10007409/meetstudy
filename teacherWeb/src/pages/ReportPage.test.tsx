import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, expect, test, vi } from 'vitest'
import contract from '../../../contracts/report-cases.json'
import type { Backend } from '../backend/Backend'
import { BackendProvider } from '../backend/context'
import { createMockBackend } from '../backend/mock'
import type { Segment, SessionDoc, WithId } from '../domain/types'
import { ReportPage } from './ReportPage'

const segs = contract.cases.find(c => c.name === 'mockupSession')!.segments as Segment[]
const session = (over: Partial<WithId<SessionDoc>> = {}): WithId<SessionDoc> => ({
  id: 'x', studentUid: 's1', roomId: null, startMs: segs[0].startMs, endMs: segs.at(-1)!.endMs, goalMs: 50 * 60_000,
  segments: segs, goalStar: true, proofStar: false, proofPhotoPath: null, ...over,
})

beforeEach(() => {
  vi.stubGlobal('matchMedia', (q: string) => ({ matches: q.includes('reduce'), addEventListener() {}, removeEventListener() {} }))
})

function show(sessions: WithId<SessionDoc>[]) {
  const backend: Backend = { ...createMockBackend(), listSessions: async () => sessions }
  render(
    <BackendProvider backend={backend}>
      <MemoryRouter initialEntries={['/students/s1/report']}>
        <Routes><Route path="/students/:uid/report/:sessionId?" element={<ReportPage />} /></Routes>
      </MemoryRouter>
    </BackendProvider>,
  )
}

test('showsMockupNumbers', async () => {
  show([session()])
  expect(await screen.findByText('53분 동안 집중했어요')).toBeInTheDocument()
  expect(screen.getByText('78%')).toBeInTheDocument()
  expect(screen.getByText('7분')).toBeInTheDocument()
  expect(screen.getByText(/목표 50분을 채웠어요/)).toBeInTheDocument()
  expect(screen.getByText('오후 2:10부터 3:18까지')).toBeInTheDocument()
})

test('noPreviousNoComparison', async () => {
  show([session()])
  await screen.findByText('53분 동안 집중했어요')
  expect(screen.queryByText(/지난번/)).not.toBeInTheDocument()
})

test('comparisonWithPrevious', async () => {
  const prev = session({ id: 'p', startMs: segs[0].startMs - 86_400_000, endMs: segs[0].startMs - 86_400_000 + 1, segments: [
    { state: 'STUDYING', startMs: segs[0].startMs - 86_400_000, endMs: segs[0].startMs - 86_400_000 + 41 * 60_000 },
  ] })
  show([prev, session()])
  expect(await screen.findByText('지난번보다 12분 늘었어요')).toBeInTheDocument()
})

test('weeklyTabShowsSevenBars', async () => {
  show([session()])
  await screen.findByText('53분 동안 집중했어요')
  await userEvent.click(screen.getByRole('tab', { name: '주간' }))
  expect(screen.getAllByTestId('bar')).toHaveLength(7)
  await userEvent.click(screen.getByRole('tab', { name: '월간' }))
  expect(screen.getAllByTestId('bar')).toHaveLength(30)
})

test('photoMissingText', async () => {
  show([session()])
  expect(await screen.findByText('사진이 아직 없어요')).toBeInTheDocument()
})

test('noSessionsYet', async () => {
  show([])
  expect(await screen.findByText('아직 공부한 기록이 없어요')).toBeInTheDocument()
})
