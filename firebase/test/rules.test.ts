import { readFileSync } from 'node:fs'
import { afterAll, beforeAll, beforeEach, describe, test } from 'vitest'
import {
  assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment,
} from '@firebase/rules-unit-testing'
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where } from 'firebase/firestore'
import { getBytes, ref, uploadBytes } from 'firebase/storage'

let env: RulesTestEnvironment

// S student, S2 second student, T approved teacher (linked to S), P pending teacher, U approved teacher not linked.
const student = (uid: string, loginId: string) => ({ role: 'student', loginId, name: uid, createdAt: 1 })
const teacher = (uid: string, status: string) => ({ role: 'teacher', loginId: uid.toLowerCase() + 'tchr', name: uid, teacherStatus: status, createdAt: 1 })
const room = (studentUids: string[]) => ({
  teacherUid: 'T', name: '중2 수학반', studentUids, awayThresholdSec: 10,
  breakLimit: { count: 3, minutes: 10 }, status: 'open', openedAt: 1,
})
const session = (studentUid: string) => ({
  studentUid, roomId: null, startMs: 1, endMs: 2, goalMs: 60000, segments: [], goalStar: false, proofStar: false, proofPhotoPath: null,
})
const presence = { state: 'STUDYING', since: 1, focusedSec: 0, goalSec: 3600, sessionStartMs: 1, segments: [], updatedAt: 1 }

const db = (uid: string | null) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore()
const st = (uid: string) => env.authenticatedContext(uid).storage()

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-meetstudy',
    firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
    storage: { rules: readFileSync('storage.rules', 'utf8'), host: '127.0.0.1', port: 9199 },
  })
})
afterAll(async () => { await env.cleanup() })

beforeEach(async () => {
  await env.clearFirestore()
  await env.clearStorage()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore()
    await setDoc(doc(f, 'users/S'), student('S', 'stud_s'))
    await setDoc(doc(f, 'users/S2'), student('S2', 'stud_s2'))
    await setDoc(doc(f, 'users/T'), teacher('T', 'approved'))
    await setDoc(doc(f, 'users/P'), teacher('P', 'pending'))
    await setDoc(doc(f, 'users/U'), teacher('U', 'approved'))
    await setDoc(doc(f, 'loginIds/abcd'), { uid: 'S' })
    await setDoc(doc(f, 'links/T_S'), { teacherUid: 'T', studentUid: 'S', teacherName: 'T', studentName: 'S', studentLoginId: 'stud_s', status: 'accepted', createdAt: 1 })
    await setDoc(doc(f, 'rooms/R'), room(['S']))
    await setDoc(doc(f, 'sessions/x1'), session('S'))
    await uploadBytes(ref(ctx.storage(), 'proofs/S/x.jpg'), new Uint8Array(10), { contentType: 'image/jpeg' })
  })
})

describe('users', () => {
  test('userCanCreateOwnDoc', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => { await deleteDoc(doc(ctx.firestore(), 'users/S')) })
    await assertSucceeds(setDoc(doc(db('S'), 'users/S'), student('S', 'stud_s')))
    await assertFails(setDoc(doc(db('S'), 'users/X'), student('X', 'xxxx')))
    await assertFails(setDoc(doc(db('N1'), 'users/N1'), teacher('N1', 'approved')))
    await assertSucceeds(setDoc(doc(db('N2'), 'users/N2'), teacher('N2', 'pending')))
    await assertFails(setDoc(doc(db('N3'), 'users/N3'), { ...student('N3', 'n3id'), role: 'admin' }))
  })

  test('cannotSelfApprove', async () => {
    await assertFails(updateDoc(doc(db('P'), 'users/P'), { teacherStatus: 'approved' }))
    await assertFails(updateDoc(doc(db('S'), 'users/S'), { role: 'teacher' }))
    await assertSucceeds(updateDoc(doc(db('S'), 'users/S'), { name: '새 이름' }))
  })
})

describe('loginIds', () => {
  test('loginIdCannotBeTaken', async () => {
    await assertSucceeds(setDoc(doc(db('S2'), 'loginIds/newid'), { uid: 'S2' }))
    await assertFails(setDoc(doc(db('U'), 'loginIds/newid'), { uid: 'U' }))
    await assertFails(setDoc(doc(db('U'), 'loginIds/other'), { uid: 'S' }))
    await assertFails(setDoc(doc(db('S'), 'loginIds/abcd'), { uid: 'S' }))
    await assertFails(deleteDoc(doc(db('S'), 'loginIds/abcd')))
  })

  test('onlyApprovedTeacherSearches', async () => {
    await assertSucceeds(getDoc(doc(db('T'), 'loginIds/abcd')))
    await assertSucceeds(getDoc(doc(db('T'), 'users/S')))
    await assertFails(getDoc(doc(db('P'), 'loginIds/abcd')))
    await assertFails(getDoc(doc(db('P'), 'users/S')))
    await assertFails(getDoc(doc(db('S'), 'users/T')))
    await assertFails(getDoc(doc(db('T'), 'users/U')))
    await assertSucceeds(getDoc(doc(db('S'), 'users/S')))
  })
})

describe('links', () => {
  const link = (t: string, s: string) => ({ teacherUid: t, studentUid: s, teacherName: t, studentName: s, studentLoginId: 'x', status: 'pending', createdAt: 1 })

  test('linkLifecycle', async () => {
    await assertSucceeds(setDoc(doc(db('T'), 'links/T_S2'), link('T', 'S2')))
    await assertFails(setDoc(doc(db('P'), 'links/P_S2'), link('P', 'S2')))
    await assertFails(setDoc(doc(db('T'), 'links/U_S2'), link('U', 'S2')))
    await assertFails(setDoc(doc(db('T'), 'links/T_S2b'), { ...link('T', 'S2'), status: 'accepted' }))
    await assertFails(updateDoc(doc(db('T'), 'links/T_S2'), { status: 'accepted' }))
    await assertSucceeds(updateDoc(doc(db('S2'), 'links/T_S2'), { status: 'accepted' }))
    await assertSucceeds(getDocs(query(collection(db('T'), 'links'), where('teacherUid', '==', 'T'))))
    await assertSucceeds(getDocs(query(collection(db('S2'), 'links'), where('studentUid', '==', 'S2'))))
    await assertFails(getDoc(doc(db('U'), 'links/T_S2')))
    await assertSucceeds(deleteDoc(doc(db('S2'), 'links/T_S2')))
    await assertSucceeds(deleteDoc(doc(db('T'), 'links/T_S')))
  })
})

describe('sessions', () => {
  test('sessionsReadOnlyWhenLinked', async () => {
    await assertSucceeds(setDoc(doc(db('S'), 'sessions/x2'), session('S')))
    await assertFails(setDoc(doc(db('S'), 'sessions/x3'), session('U')))
    await assertSucceeds(getDoc(doc(db('T'), 'sessions/x1')))
    await assertFails(getDoc(doc(db('U'), 'sessions/x1')))
    await assertSucceeds(getDocs(query(collection(db('T'), 'sessions'), where('studentUid', '==', 'S'))))
    await assertFails(getDocs(query(collection(db('U'), 'sessions'), where('studentUid', '==', 'S'))))
    await assertFails(setDoc(doc(db('S2'), 'sessions/x1'), session('S2')))
  })
})

describe('rooms', () => {
  test('roomsOwnerOnly', async () => {
    await assertSucceeds(setDoc(doc(db('T'), 'rooms/R2'), room(['a', 'b', 'c', 'd', 'e', 'f'])))
    await assertFails(setDoc(doc(db('T'), 'rooms/R3'), room(['a', 'b', 'c', 'd', 'e', 'f', 'g'])))
    await assertFails(setDoc(doc(db('P'), 'rooms/R4'), { ...room(['S']), teacherUid: 'P' }))
    await assertFails(setDoc(doc(db('U'), 'rooms/R5'), room(['S'])))
    await assertSucceeds(getDoc(doc(db('S'), 'rooms/R')))
    await assertFails(getDoc(doc(db('S2'), 'rooms/R')))
    await assertFails(updateDoc(doc(db('S'), 'rooms/R'), { name: 'x' }))
    await assertSucceeds(updateDoc(doc(db('T'), 'rooms/R'), { status: 'closed', closedAt: 2 }))
    await assertSucceeds(getDocs(query(collection(db('S'), 'rooms'), where('studentUids', 'array-contains', 'S'), where('status', '==', 'open'))))
    await assertSucceeds(getDocs(query(collection(db('T'), 'rooms'), where('teacherUid', '==', 'T'))))
  })

  test('presence', async () => {
    await assertSucceeds(setDoc(doc(db('S'), 'rooms/R/presence/S'), presence))
    await assertFails(setDoc(doc(db('S'), 'rooms/R/presence/U'), presence))
    await assertSucceeds(getDoc(doc(db('T'), 'rooms/R/presence/S')))
    await assertSucceeds(getDocs(collection(db('T'), 'rooms/R/presence')))
    await assertFails(getDoc(doc(db('U'), 'rooms/R/presence/S')))
    await assertFails(setDoc(doc(db('S2'), 'rooms/R/presence/S2'), presence))
  })
})

describe('storage', () => {
  test('proofPhotos', async () => {
    const img = (n: number) => new Uint8Array(n)
    await assertSucceeds(uploadBytes(ref(st('S'), 'proofs/S/y.jpg'), img(100 * 1024), { contentType: 'image/jpeg' }))
    await assertFails(uploadBytes(ref(st('S'), 'proofs/U/y.jpg'), img(100), { contentType: 'image/jpeg' }))
    await assertFails(uploadBytes(ref(st('S'), 'proofs/S/big.jpg'), img(6 * 1024 * 1024), { contentType: 'image/jpeg' }))
    await assertFails(uploadBytes(ref(st('S'), 'proofs/S/z.txt'), img(100), { contentType: 'text/plain' }))
    await assertSucceeds(getBytes(ref(st('T'), 'proofs/S/x.jpg')))
    await assertFails(getBytes(ref(st('U'), 'proofs/S/x.jpg')))
    await assertSucceeds(getBytes(ref(st('S'), 'proofs/S/x.jpg')))
  })
})
