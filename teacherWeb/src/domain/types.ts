// Mirrors the Firestore schema in docs/superpowers/plans/2026-11-13-teacher-web-firebase.md.
export type FocusState =
  | 'STUDYING' | 'AWAY' | 'LOOKING_AWAY' | 'CAMERA_GAZE' | 'DROWSY' | 'PHONE' | 'APP_LEFT' | 'BREAK'

export interface Segment { state: FocusState; startMs: number; endMs: number }

export type TeacherStatus = 'pending' | 'approved'

export interface UserDoc {
  role: 'student' | 'teacher'
  loginId: string
  name: string
  teacherStatus?: TeacherStatus
  createdAt: number
}

export interface LinkDoc {
  teacherUid: string
  studentUid: string
  teacherName: string
  studentName: string
  studentLoginId: string
  status: 'pending' | 'accepted'
  createdAt: number
}

export interface RoomDoc {
  teacherUid: string
  name: string
  studentUids: string[]
  awayThresholdSec: number
  breakLimit: { count: number; minutes: number }
  status: 'open' | 'closed'
  openedAt: number
  closedAt?: number
}

/** Presence as the web sees it: Firestore's serverTimestamp `updatedAt` turned into epoch ms. */
export interface Presence {
  state: FocusState
  since: number
  focusedSec: number
  goalSec: number
  sessionStartMs: number
  segments: Segment[]
  updatedAtMs: number
}

export interface SessionDoc {
  studentUid: string
  roomId: string | null
  startMs: number
  endMs: number
  goalMs: number
  segments: Segment[]
  goalStar: boolean
  proofStar: boolean
  proofPhotoPath: string | null
}

export type WithId<T> = T & { id: string }
