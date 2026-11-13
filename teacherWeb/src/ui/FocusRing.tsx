import { useEffect, useState } from 'react'
import './ui.css'

const R = 52
const C = 2 * Math.PI * R

const reducedMotion = () => typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** Focus-rate ring. Fills once on open (the page's only motion); reduced motion shows the value at once. */
export function FocusRing({ pct }: { pct: number }) {
  const [shown, setShown] = useState(() => (reducedMotion() ? pct : 0))
  useEffect(() => {
    if (reducedMotion()) { setShown(pct); return }
    let raf = 0
    const t0 = performance.now()
    const step = (now: number) => {
      const k = Math.min(1, (now - t0) / 900)
      setShown(pct * (1 - Math.pow(1 - k, 3)))
      if (k < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [pct])
  return (
    <div className="ring" role="img" aria-label={`집중률 ${pct}%`}>
      <svg viewBox="0 0 120 120" aria-hidden="true">
        <circle cx="60" cy="60" r={R} fill="none" stroke="#E3E8E6" strokeWidth="12" />
        <circle cx="60" cy="60" r={R} fill="none" stroke="var(--teal)" strokeWidth="12" strokeLinecap="round" strokeDasharray={`${(C * shown) / 100} ${C}`} />
      </svg>
      <div className="v" aria-hidden="true"><b>{Math.round(shown)}%</b><span>집중률</span></div>
    </div>
  )
}
