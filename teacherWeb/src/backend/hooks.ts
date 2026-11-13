import { useEffect, useState } from 'react'
import type { Unsubscribe } from './Backend'

/** Value from a `watch*` subscription; `initial` until the first callback. */
export function useWatch<T>(subscribe: (cb: (v: T) => void) => Unsubscribe, initial: T, deps: unknown[]): T {
  const [v, setV] = useState<T>(initial)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => subscribe(setV), deps)
  return v
}

/** Wall clock that re-renders every [ms]; tiles count seconds and go offline without new data. */
export function useNow(ms = 1_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), ms); return () => clearInterval(t) }, [ms])
  return now
}
