import { createContext, useContext, type ReactNode } from 'react'
import type { Backend } from './Backend'

const Ctx = createContext<Backend | null>(null)

export function BackendProvider({ backend, children }: { backend: Backend; children: ReactNode }) {
  return <Ctx.Provider value={backend}>{children}</Ctx.Provider>
}

export function useBackend(): Backend {
  const b = useContext(Ctx)
  if (!b) throw new Error('BackendProvider missing')
  return b
}
