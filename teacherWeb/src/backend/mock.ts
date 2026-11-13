import { isValidLoginId, normalizeLoginId } from '../domain/loginId'
import { focusedMs } from '../domain/report'
import type { FocusState, LinkDoc, Presence, RoomDoc, Segment, SessionDoc, WithId } from '../domain/types'
import { BackendError, MAX_ROOM_STUDENTS, type Backend, type Me, type RoomInput, type StudentHit } from './Backend'

// Screens without keys: the mockup's teacher, students and rooms, with presence that moves every 3 s.
const TICK_MS = 3_000
const MIN = 60_000
const FROZEN = 's5' // 정우: the app was closed, so the teacher sees 오프라인 after a minute

const STUDENTS: StudentHit[] = [
  ['s1', '김하늘', 'haneul'], ['s2', '이준', 'jun_lee'], ['s3', '박서연', 'seoyeon'], ['s4', '최민', 'choimin'],
  ['s5', '정우', 'jungwoo'], ['s6', '한결', 'hangyeol'], ['s7', '윤아', 'yuna'], ['s8', '서준', 'seojun'],
  ['s9', '지민', 'jimin'], ['s10', '도윤', 'doyoon'],
].map(([uid, name, loginId]) => ({ uid, name, loginId }))

const TEACHER: Me = { uid: 't1', name: '김선생님', teacherStatus: 'approved' }
const TEACHER_IDS = new Set(['teacher_kim'])

/** Small seeded generator so the mock looks the same on every load and in tests. */
function rng(seed: number) {
  let s = seed >>> 0
  return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 2 ** 32 }
}

const DISTRACT: FocusState[] = ['LOOKING_AWAY', 'LOOKING_AWAY', 'AWAY', 'CAMERA_GAZE', 'DROWSY', 'PHONE']

/** A plausible session: mostly study with short distractions and maybe one break. */
function fakeSegments(rand: () => number, startMs: number, lengthMin: number): Segment[] {
  const out: Segment[] = []
  let t = startMs
  const end = startMs + lengthMin * MIN
  let breakUsed = false
  while (t < end) {
    const study = (6 + rand() * 18) * MIN
    out.push({ state: 'STUDYING', startMs: t, endMs: Math.min(end, t + study) })
    t += study
    if (t >= end) break
    const isBreak: boolean = !breakUsed && rand() < 0.25
    breakUsed ||= isBreak
    const d = (isBreak ? 6 + rand() * 4 : 1 + rand() * 3) * MIN
    out.push({ state: isBreak ? 'BREAK' : DISTRACT[Math.floor(rand() * DISTRACT.length)], startMs: t, endMs: Math.min(end, t + d) })
    t += d
  }
  return out.map(s => ({ ...s, startMs: Math.round(s.startMs), endMs: Math.round(s.endMs) }))
}

export function createMockBackend(opts: { signedIn?: boolean } = {}): Backend {
  const rand = rng(7)
  const now = () => Date.now()
  const t0 = now()

  let me: Me | null = opts.signedIn === false ? null : TEACHER
  const meSubs = new Set<(m: Me | null) => void>()
  const setMe = (m: Me | null) => { me = m; meSubs.forEach(cb => cb(me)) }

  const linkOf = (s: StudentHit, status: LinkDoc['status']): LinkDoc => ({
    teacherUid: TEACHER.uid, studentUid: s.uid, teacherName: TEACHER.name, studentName: s.name,
    studentLoginId: s.loginId, status, createdAt: t0 - 30 * 24 * 60 * MIN,
  })
  let links: LinkDoc[] = [...STUDENTS.slice(0, 8).map(s => linkOf(s, 'accepted')), linkOf(STUDENTS[8], 'pending')]
  const linkSubs = new Set<(l: LinkDoc[]) => void>()
  const emitLinks = () => linkSubs.forEach(cb => cb(links))

  const room = (id: string, name: string, uids: string[], openedAgoMin: number, closed = false): WithId<RoomDoc> => ({
    id, teacherUid: TEACHER.uid, name, studentUids: uids, awayThresholdSec: 10, breakLimit: { count: 3, minutes: 10 },
    status: closed ? 'closed' : 'open', openedAt: t0 - openedAgoMin * MIN, ...(closed ? { closedAt: t0 - (openedAgoMin - 90) * MIN } : {}),
  })
  let rooms: WithId<RoomDoc>[] = [
    room('r1', '중2 수학반', ['s1', 's2', 's3', 's4', 's5'], 72),
    room('r2', '초6 영어반', ['s6', 's7', 's8'], 25),
    room('r3', '주말 자습', ['s1', 's2', 's6'], 24 * 60, true),
  ]
  const roomSubs = new Set<(r: WithId<RoomDoc>[]) => void>()
  const emitRooms = () => roomSubs.forEach(cb => cb(rooms))

  // Live presence per open room. Each student started a little after the room opened.
  const presence = new Map<string, Record<string, Presence>>()
  const startPresence = (r: WithId<RoomDoc>) => {
    const ps: Record<string, Presence> = {}
    for (const uid of r.studentUids) {
      const start = r.openedAt + Math.round(rand() * 8) * MIN
      const segs = fakeSegments(rand, start, Math.max(1, (t0 - start) / MIN))
      segs[segs.length - 1].endMs = t0
      const last = segs[segs.length - 1]
      ps[uid] = {
        state: last.state, since: last.startMs, focusedSec: Math.round(focusedMs(segs) / 1000), goalSec: (uid === FROZEN ? 40 : 60) * 60,
        sessionStartMs: start, segments: segs, updatedAtMs: uid === FROZEN ? t0 - 2 * MIN : t0,
      }
    }
    presence.set(r.id, ps)
  }
  rooms.filter(r => r.status === 'open').forEach(startPresence)
  const presenceSubs = new Map<string, Set<(p: Record<string, Presence>) => void>>()
  const emitPresence = (roomId: string) => presenceSubs.get(roomId)?.forEach(cb => cb({ ...(presence.get(roomId) ?? {}) }))

  const tick = () => {
    const t = now()
    for (const [roomId, ps] of presence) {
      for (const [uid, p] of Object.entries(ps)) {
        if (uid === FROZEN) continue
        const segs = p.segments.map(s => ({ ...s }))
        segs[segs.length - 1].endMs = t
        const cur = segs[segs.length - 1].state
        // Distractions end quickly; study occasionally breaks for a distraction.
        const next: FocusState = cur === 'STUDYING' ? (rand() < 0.08 ? DISTRACT[Math.floor(rand() * DISTRACT.length)] : 'STUDYING')
          : cur === 'BREAK' ? (rand() < 0.1 ? 'STUDYING' : 'BREAK')
          : (rand() < 0.35 ? 'STUDYING' : cur)
        if (next !== cur) segs.push({ state: next, startMs: t, endMs: t })
        const last = segs[segs.length - 1]
        ps[uid] = { ...p, state: last.state, since: last.startMs, segments: segs, focusedSec: Math.round(focusedMs(segs) / 1000), updatedAtMs: t }
      }
      emitPresence(roomId)
    }
  }
  setInterval(tick, TICK_MS)

  // Two weeks of finished sessions per linked student, one or none per day.
  const sessions: WithId<SessionDoc>[] = []
  STUDENTS.slice(0, 9).forEach((s, i) => {
    for (let d = 13; d >= 1; d--) {
      if (rand() < 0.3) continue
      const day = new Date(t0 - d * 24 * 60 * MIN)
      day.setUTCHours(6 + Math.floor(rand() * 6), Math.floor(rand() * 60), 0, 0) // 15:00-21:00 KST
      const len = 30 + Math.floor(rand() * 60)
      const segs = fakeSegments(rand, day.getTime(), len)
      const goalMs = (i % 3 === 0 ? 40 : 60) * MIN
      const reached = focusedMs(segs) >= goalMs
      sessions.push({
        id: `${s.uid}-${d}`, studentUid: s.uid, roomId: null, startMs: segs[0].startMs, endMs: segs.at(-1)!.endMs, goalMs,
        segments: segs, goalStar: reached, proofStar: reached && rand() < 0.7, proofPhotoPath: reached ? `proofs/${s.uid}/${s.uid}-${d}.jpg` : null,
      })
    }
  })

  const findStudent = (loginId: string) => STUDENTS.find(s => s.loginId === normalizeLoginId(loginId)) ?? null

  return {
    async signIn(loginId, password) {
      if (!isValidLoginId(normalizeLoginId(loginId)) || password.length < 6) throw new BackendError('wrong-login')
      setMe(TEACHER)
    },
    async signUpTeacher(loginId, password, name) {
      const id = normalizeLoginId(loginId)
      if (!isValidLoginId(id)) throw new BackendError('invalid-id')
      if (password.length < 6) throw new BackendError('weak-password')
      if (findStudent(id) || TEACHER_IDS.has(id)) throw new BackendError('id-taken')
      TEACHER_IDS.add(id)
      setMe({ uid: 't-new', name, teacherStatus: 'pending' })
      // The operator approves in the console; here it happens by itself so the live flip can be seen.
      setTimeout(() => { if (me?.uid === 't-new') setMe({ ...me, teacherStatus: 'approved' }) }, 4_000)
    },
    async signOut() { setMe(null) },
    watchMe(cb) { meSubs.add(cb); cb(me); return () => { meSubs.delete(cb) } },

    async findStudent(loginId) { return findStudent(loginId) },
    async requestLink(s) {
      if (links.some(l => l.studentUid === s.uid)) return
      links = [...links, linkOf(s, 'pending')]
      emitLinks()
      // The student accepts in the app a moment later.
      setTimeout(() => { links = links.map(l => (l.studentUid === s.uid ? { ...l, status: 'accepted' } : l)); emitLinks() }, 5_000)
    },
    watchLinks(cb) { linkSubs.add(cb); cb(links); return () => { linkSubs.delete(cb) } },
    async unlink(uid) { links = links.filter(l => l.studentUid !== uid); emitLinks() },

    watchRooms(cb) { roomSubs.add(cb); cb(rooms); return () => { roomSubs.delete(cb) } },
    async createRoom(input: RoomInput) {
      if (input.studentUids.length > MAX_ROOM_STUDENTS) throw new BackendError('too-many')
      const id = `r${rooms.length + 1}-${now()}`
      rooms = [...rooms, { id, teacherUid: TEACHER.uid, ...input, status: 'open', openedAt: now() }]
      presence.set(id, {}) // invited students show as "아직 안 들어왔어요" until they join
      emitRooms()
      return id
    },
    async updateRoom(id, patch) {
      if (patch.studentUids && patch.studentUids.length > MAX_ROOM_STUDENTS) throw new BackendError('too-many')
      rooms = rooms.map(r => (r.id === id ? { ...r, ...patch } : r))
      // A student taken out of the room (내보내기) stops showing at once.
      const ps = presence.get(id)
      if (ps && patch.studentUids) for (const uid of Object.keys(ps)) if (!patch.studentUids.includes(uid)) delete ps[uid]
      emitRooms(); emitPresence(id)
    },
    async closeRoom(id) {
      rooms = rooms.map(r => (r.id === id ? { ...r, status: 'closed', closedAt: now() } : r))
      presence.delete(id)
      emitRooms(); emitPresence(id)
    },
    watchPresence(roomId, cb) {
      if (!presenceSubs.has(roomId)) presenceSubs.set(roomId, new Set())
      presenceSubs.get(roomId)!.add(cb)
      cb({ ...(presence.get(roomId) ?? {}) })
      return () => { presenceSubs.get(roomId)?.delete(cb) }
    },

    async listSessions(uid, sinceMs) {
      const live = [...presence.entries()].flatMap(([roomId, ps]) => {
        const p = ps[uid]
        return p ? [{ id: `${uid}-live`, studentUid: uid, roomId, startMs: p.sessionStartMs, endMs: p.segments.at(-1)!.endMs,
          goalMs: p.goalSec * 1000, segments: p.segments, goalStar: false, proofStar: false, proofPhotoPath: null }] : []
      })
      return [...sessions, ...live].filter(s => s.studentUid === uid && s.startMs >= sinceMs).sort((a, b) => a.startMs - b.startMs)
    },
    async proofPhotoUrl(path) {
      if (!path) return null
      // Lined-paper placeholder in place of a real workbook photo.
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="400" height="300" fill="#fff"/>${
        Array.from({ length: 14 }, (_, i) => `<line x1="24" x2="376" y1="${30 + i * 18}" y2="${30 + i * 18}" stroke="#DCE3E1"/>`).join('')}</svg>`
      return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
    },
  }
}
