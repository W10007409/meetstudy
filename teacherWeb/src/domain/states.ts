import type { FocusState } from './types'

// Same colours and words as the student app (shared/.../ui/theme/StateColors.kt).
export const STATE_COLOR: Record<FocusState, string> = {
  STUDYING: '#4CC38A',
  AWAY: '#9AA3AE',
  LOOKING_AWAY: '#FF9F43',
  CAMERA_GAZE: '#FF9F43',
  DROWSY: '#FF9F43',
  PHONE: '#FF9F43',
  APP_LEFT: '#FF9F43',
  BREAK: '#5AA9FF',
}
export const OFFLINE_COLOR = '#FF6B6B'

export const STATE_LABEL: Record<FocusState, string> = {
  STUDYING: '공부 중',
  AWAY: '자리 비움',
  LOOKING_AWAY: '다른 곳 보는 중',
  CAMERA_GAZE: '카메라 보는 중',
  DROWSY: '졸고 있어요',
  PHONE: '폰 사용 중',
  APP_LEFT: '앱 나감',
  BREAK: '휴식 중',
}

/** "흐트러진 시간": any distraction. BREAK is allowed rest. */
export const LOST_STATES: ReadonlySet<FocusState> = new Set(['AWAY', 'LOOKING_AWAY', 'CAMERA_GAZE', 'DROWSY', 'PHONE', 'APP_LEFT'])

/** One past-tense line per event in the report ("2:31 다른 곳을 봤어요 · 3분"). */
export const EVENT_LABEL: Record<Exclude<FocusState, 'STUDYING'>, string> = {
  LOOKING_AWAY: '다른 곳을 봤어요',
  CAMERA_GAZE: '화면을 오래 봤어요',
  AWAY: '자리를 비웠어요',
  DROWSY: '졸았어요',
  PHONE: '폰을 봤어요',
  APP_LEFT: '앱을 나갔어요',
  BREAK: '쉬었어요',
}
