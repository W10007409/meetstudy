import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { BackendProvider } from '../backend/context'
import { createMockBackend } from '../backend/mock'
import { Shell } from '../Shell'
import { RoomPage } from './RoomPage'

beforeEach(() => { vi.useFakeTimers({ shouldAdvanceTime: true }); vi.setSystemTime(new Date('2026-11-13T06:20:00Z')) })
afterEach(() => vi.useRealTimers())

function show(path = '/rooms/r1') {
  const backend = createMockBackend()
  render(
    <BackendProvider backend={backend}>
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route element={<Shell />}><Route path="/rooms/:id" element={<RoomPage />} /></Route></Routes>
      </MemoryRouter>
    </BackendProvider>,
  )
  return backend
}

test('fiveTilesAndOneFreeSeat', () => {
  show()
  expect(screen.getAllByTestId('tile')).toHaveLength(5)
  expect(screen.getByText('+ 학생 초대하기 (1자리 남음)')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '중2 수학반' })).toBeInTheDocument()
  expect(screen.getByText(/자리 비움은 10초, 휴식은 3번까지 10분씩이에요/)).toBeInTheDocument()
})

test('frozenStudentShowsOfflineAfterAMinute', () => {
  show()
  const tile = () => screen.getAllByTestId('tile').find(t => within(t).queryByText('정우'))!
  act(() => { vi.advanceTimersByTime(61_000) })
  expect(within(tile()).getByText('오프라인')).toBeInTheDocument()
  expect(screen.getByTestId('pulse')).toHaveTextContent('오프라인')
})

test('navListsRooms', () => {
  show()
  const nav = screen.getByRole('navigation')
  expect(within(nav).getByText('중2 수학반')).toBeInTheDocument()
  expect(within(nav).getByText('주말 자습')).toBeInTheDocument()
  expect(within(nav).getByText('+ 방 만들기')).toBeInTheDocument()
})

test('closeRoomAsksFirst', async () => {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
  show()
  await user.click(screen.getByRole('button', { name: '방 닫기' }))
  const dialog = screen.getByRole('dialog')
  expect(dialog).toHaveTextContent('공부 중인 학생은 혼자 공부로 이어서 해요')
  await user.click(within(dialog).getByRole('button', { name: '닫기' }))
  expect(await screen.findByText('닫힌 방이에요')).toBeInTheDocument()
})
