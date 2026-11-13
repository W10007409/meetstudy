import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import contract from '../../../contracts/report-cases.json'
import type { Segment } from '../domain/types'
import { FocusRing } from './FocusRing'
import { Planner } from './Planner'
import { PlannerStrip } from './PlannerStrip'
import { StatusBadge } from './StatusBadge'

const mockup = contract.cases.find(c => c.name === 'mockupSession')!.segments as Segment[]

afterEach(() => vi.unstubAllGlobals())

test('plannerDrawsHoursOfSlivers', () => {
  render(<Planner segments={mockup} />)
  const rows = screen.getAllByTestId('planner-row')
  expect(rows).toHaveLength(2)
  expect(rows[0]).toHaveTextContent('2시')
  const slivers = rows[0].querySelectorAll('[data-minute]')
  expect(slivers).toHaveLength(60)
  expect(slivers[31].getAttribute('data-state')).toBe('LOOKING_AWAY')
  expect(slivers[5].getAttribute('data-state')).toBe('')
})

test('stripShowsOnlyTheCurrentHour', () => {
  const now = mockup[0].startMs + 50 * 60_000 // 15:00 KST
  render(<PlannerStrip segments={mockup} nowMs={now} />)
  expect(screen.getAllByTestId('planner-row')).toHaveLength(1)
  expect(screen.getByText('3시')).toBeInTheDocument()
  expect(screen.getByText('3시 50분')).toBeInTheDocument()
})

test('badgeText', () => {
  render(<StatusBadge state="BREAK" detail="4분 남음" />)
  expect(screen.getByText('휴식 중 · 4분 남음')).toBeInTheDocument()
})

test('badgeOffline', () => {
  render(<StatusBadge state="offline" />)
  expect(screen.getByText('오프라인')).toBeInTheDocument()
})

test('ringReducedMotionShowsFinalValue', () => {
  vi.stubGlobal('matchMedia', (q: string) => ({ matches: q.includes('reduce'), addEventListener() {}, removeEventListener() {} }))
  render(<FocusRing pct={78} />)
  expect(screen.getByText('78%')).toBeInTheDocument()
})
