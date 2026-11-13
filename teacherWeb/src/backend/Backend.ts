import type { LinkDoc, Presence, RoomDoc, SessionDoc, TeacherStatus, WithId } from '../domain/types'

export type BackendErrorCode = 'id-taken' | 'weak-password' | 'invalid-id' | 'wrong-login' | 'network' | 'too-many' | 'not-found'

export class BackendError extends Error {
  constructor(readonly code: BackendErrorCode) { super(code) }
}

export interface Me { uid: string; name: string; teacherStatus: TeacherStatus }
export interface StudentHit { uid: string; name: string; loginId: string }
export interface RoomInput { name: string; studentUids: string[]; awayThresholdSec: number; breakLimit: { count: number; minutes: number } }
export type Unsubscribe = () => void

/** Everything the teacher web reads or writes. `mock` for screens without keys, `firebase` for the real project. */
export interface Backend {
  signIn(loginId: string, password: string): Promise<void>
  signUpTeacher(loginId: string, password: string, name: string): Promise<void>
  signOut(): Promise<void>
  /** Called at once with the current value, then on every change (approval flips it live). */
  watchMe(cb: (me: Me | null) => void): Unsubscribe

  findStudent(loginId: string): Promise<StudentHit | null>
  requestLink(student: StudentHit): Promise<void>
  watchLinks(cb: (links: LinkDoc[]) => void): Unsubscribe
  unlink(studentUid: string): Promise<void>

  watchRooms(cb: (rooms: WithId<RoomDoc>[]) => void): Unsubscribe
  createRoom(input: RoomInput): Promise<string>
  updateRoom(id: string, patch: Partial<RoomInput>): Promise<void>
  closeRoom(id: string): Promise<void>
  watchPresence(roomId: string, cb: (p: Record<string, Presence>) => void): Unsubscribe

  listSessions(studentUid: string, sinceMs: number): Promise<WithId<SessionDoc>[]>
  proofPhotoUrl(path: string): Promise<string | null>
}

export const MAX_ROOM_STUDENTS = 6
