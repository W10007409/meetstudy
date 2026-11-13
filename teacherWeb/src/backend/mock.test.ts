import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { BackendError } from './Backend'
import { createMockBackend } from './mock'
import type { Presence } from '../domain/types'

beforeEach(() => { vi.useFakeTimers(); vi.setSystemTime(new Date('2026-11-13T06:20:00Z')) })
afterEach(() => vi.useRealTimers())

const latest = (b: ReturnType<typeof createMockBackend>, roomId: string) => {
  let snap: Record<string, Presence> = {}
  const off = b.watchPresence(roomId, p => { snap = p })
  return { get: () => snap, off }
}

test('presenceAdvances', () => {
  const b = createMockBackend()
  const w = latest(b, 'r1')
  const before = Object.values(w.get()).reduce((a, p) => a + p.segments.length + p.segments.at(-1)!.endMs, 0)
  vi.advanceTimersByTime(3_000)
  const after = Object.values(w.get()).reduce((a, p) => a + p.segments.length + p.segments.at(-1)!.endMs, 0)
  expect(after).toBeGreaterThan(before)
  w.off()
})

test('frozenStudentGoesStale', () => {
  const b = createMockBackend()
  const w = latest(b, 'r1')
  vi.advanceTimersByTime(61_000)
  expect(w.get()['s5'].updatedAtMs).toBeLessThan(Date.now() - 60_000)
  expect(w.get()['s1'].updatedAtMs).toBeGreaterThan(Date.now() - 5_000)
  w.off()
})

test('createRoomLimitsSix', async () => {
  const b = createMockBackend()
  const seven = ['s1', 's2', 's3', 's4', 's5', 's6', 's7']
  await expect(b.createRoom({ name: 'x', studentUids: seven, awayThresholdSec: 10, breakLimit: { count: 3, minutes: 10 } }))
    .rejects.toEqual(new BackendError('too-many'))
  const id = await b.createRoom({ name: '새 방', studentUids: seven.slice(0, 6), awayThresholdSec: 10, breakLimit: { count: 3, minutes: 10 } })
  let names: string[] = []
  b.watchRooms(rs => { names = rs.map(r => r.name) })
  expect(names).toContain('새 방')
  expect(id).toBeTruthy()
})

test('closeRoomMovesToClosed', async () => {
  const b = createMockBackend()
  let status = ''
  b.watchRooms(rs => { status = rs.find(r => r.id === 'r1')!.status })
  expect(status).toBe('open')
  await b.closeRoom('r1')
  expect(status).toBe('closed')
})

test('signUpTeacherIdTaken', async () => {
  const b = createMockBackend()
  await expect(b.signUpTeacher('haneul', 'secret1', '새 선생님')).rejects.toEqual(new BackendError('id-taken'))
  await expect(b.signUpTeacher('ab', 'secret1', '새 선생님')).rejects.toEqual(new BackendError('invalid-id'))
  await expect(b.signUpTeacher('newteacher', '123', '새 선생님')).rejects.toEqual(new BackendError('weak-password'))
})

test('newTeacherStartsPendingThenApproved', async () => {
  const b = createMockBackend({ signedIn: false })
  let status: string | undefined
  b.watchMe(me => { status = me?.teacherStatus })
  expect(status).toBeUndefined()
  await b.signUpTeacher('newteacher', 'secret1', '새 선생님')
  expect(status).toBe('pending')
  vi.advanceTimersByTime(5_000)
  expect(status).toBe('approved')
})
