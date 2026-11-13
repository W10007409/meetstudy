# Teacher Web + Report Redesign + Firebase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a teacher web app (room management + live status + reports) and redesign the student app report. Connect both to the user's own Firebase project (id/password login, Firestore, Storage proof photos). Keep a mock mode that needs no keys.

**Architecture:**
- **Firebase**: Firestore holds users, links, rooms, live presence and sessions. Storage holds proof photos. Security rules enforce who sees what, and Firebase-emulator tests prove them.
- **Teacher web**: React app behind one `Backend` interface with `mock` and `firebase` implementations.
- **Student app**: Kotlin app behind one `Cloud` interface in `shared/commonMain`, with an Android Firebase implementation. A local-only implementation keeps today's behaviour when `google-services.json` is absent.
- **Report maths**: lives in Kotlin and TypeScript. One JSON contract file of cases keeps the two in step.

**Tech Stack:**
- Web: React 19 + TypeScript + Vite 7, react-router 7, Vitest + Testing Library, Firebase JS SDK 12.
- Rules: Firebase CLI (`npx firebase-tools`) emulators, `@firebase/rules-unit-testing`.
- Kotlin: Kotlin 2.2.21 / CMP 1.10.0, Firebase Android BoM 34.x (auth, firestore, storage), `kotlinx-coroutines-play-services`.

**Spec:** [docs/superpowers/specs/2026-11-13-teacher-web-design.md](../specs/2026-11-13-teacher-web-design.md). The mockups are next to it: `2026-11-13-teacher-web/teacher-room.html` and `report.html`.

## Global Constraints

**Design**
- Use the state colours from `StateColors.kt` verbatim:
  - STUDYING #4CC38A
  - AWAY #9AA3AE
  - LOOKING_AWAY, CAMERA_GAZE, DROWSY, PHONE, APP_LEFT #FF9F43
  - BREAK #5AA9FF
  - offline #FF6B6B
- State labels are the student app's `stateLabel()` strings verbatim: 공부 중, 자리 비움, 다른 곳 보는 중, 카메라 보는 중, 졸고 있어요, 폰 사용 중, 앱 나감, 휴식 중.
- Design tokens:
  - paper #F5F7F6, sheet #FFFFFF
  - ink #18212B, #56626F, #9AA5B1
  - rule #E3E8E6
  - teal #1F8A82, highlighter #FFE45C
  - empty planner cell #EEF2F1
- Font: IBM Plex Sans KR only.
- No all-caps labels. No "→" in button text. No red warnings, blinking, sounds or student rankings on the teacher side.
- Copy is short polite Korean ("53분 동안 집중했어요").
- Motion: only the report ring fills once on open, and it respects reduced-motion.

**Data**
- Every day boundary uses **Asia/Seoul**, in both Kotlin and TS.
- "Lost time" (흐트러진 시간) = AWAY + LOOKING_AWAY + CAMERA_GAZE + DROWSY + PHONE + APP_LEFT. BREAK is excluded.
- Focus rate = focused ÷ total, displayed as `round(rate × 100)` %.
- "Previous session" = the same student's session with the greatest `startMs` below this one's.
- Offline: presence `updatedAt` older than 60 s.
- Room max 6 students.
- Defaults: away threshold 10 s; breaks 3 × 10 min.

**Login and keys**
- Login is id/password only.
  - `loginId` matches `^[a-z0-9_]{4,20}$`, lower-cased before use.
  - The Firebase email is `${loginId}@id.meetstudy.app`.
  - Password is at least 6 characters.
- Keys never go into git or chat.
  - `studentApp/google-services.json` and `teacherWeb/.env.local` are git-ignored, and the user places them.
  - The user runs `firebase login` themselves.

**Privacy**
- No face embeddings, video or OCR text leave the device.
- Logs never contain images or OCR text.

**Builds**
- Kotlin: run `JAVA_HOME=/c/Users/10007409/.jdks/jbr-21.0.11 ./gradlew --no-daemon … < /dev/null`.
- Firebase emulators also need that `JAVA_HOME` (Java 11+).

### Firestore / Storage schema (all tasks use exactly this)

```
users/{uid}            role: "student"|"teacher", loginId, name, teacherStatus?: "pending"|"approved" (teachers only), createdAt
loginIds/{loginId}     uid
links/{teacherUid}_{studentUid}
                       teacherUid, studentUid, teacherName, studentName, studentLoginId, status: "pending"|"accepted", createdAt
rooms/{roomId}         teacherUid, name, studentUids: string[], awayThresholdSec, breakLimit: {count, minutes},
                       status: "open"|"closed", openedAt (ms), closedAt?: ms
rooms/{roomId}/presence/{studentUid}
                       state (FocusState name), since (ms), focusedSec, goalSec, sessionStartMs,
                       segments: [{state, startMs, endMs}], updatedAt (serverTimestamp)
sessions/{sessionId}   (id = SessionRecord.id) studentUid, roomId?: string|null, startMs, endMs, goalMs,
                       segments: [{state, startMs, endMs}], goalStar, proofStar, proofPhotoPath?: string|null
Storage proofs/{studentUid}/{sessionId}.jpg   image/jpeg, < 5 MB
```

## Review Focus

1. **A student's presence goes stale** (app killed, network lost). The teacher tile must flip to 오프라인 60 s after the last `updatedAt` and not keep the last state. → Task 7 test `offlineAfter60s`.
2. **Two people sign up with the same loginId at once.** Exactly one gets it, and the other sees "이미 쓰는 아이디예요". No half-created user is left behind. → Task 1 rule test `loginIdCannotBeTaken`, and Task 11 `SignUpFailsCleanlyWhenIdTaken`.
3. **The session ends while offline.** The record is kept locally and uploaded later, exactly once (doc id = record id). The photo retries separately, and a photo failure never blocks the session. → Task 13 `UploadQueueTest`.
4. **An unapproved, or approved-but-unlinked, teacher** cannot read any student's sessions, presence or photos, and cannot create rooms. → Task 1 rule tests.
5. **A session spans midnight or a day boundary.** It belongs to its start date in Asia/Seoul on both platforms, and planner rows continue past 23 → 0. → Task 2 contract case `crossesMidnight`.

---

### Task 1: Firebase project files, security rules, rule tests

**Files:**
- Create: `firebase/firebase.json`, `firebase/firestore.rules`, `firebase/storage.rules`, `firebase/firestore.indexes.json`
- Create: `firebase/package.json`, `firebase/vitest.config.ts`, `firebase/test/rules.test.ts`
- Modify: `.gitignore` (add `firebase/node_modules/`, `firebase/*.log`, `teacherWeb/.env.local`, `teacherWeb/node_modules/`, `teacherWeb/dist/`, `studentApp/google-services.json`)

**Interfaces:**
- Produces: the rules every later task relies on, and `firebase.json` with these settings:
  - hosting `public: "../teacherWeb/dist"` with an SPA rewrite to `/index.html`
  - emulators: auth 9099, firestore 8080, storage 9199, ui off
- Produces: `npm test` in `firebase/`, which runs `firebase emulators:exec --only firestore,storage "vitest run"`.

- [ ] **Step 1: Write the failing rule tests** in `firebase/test/rules.test.ts` with `@firebase/rules-unit-testing`. Seed with `withSecurityRulesDisabled`. Use `assertSucceeds` / `assertFails`.
  - **Fixtures**
    - student S (`users/S` role student)
    - approved teacher T
    - pending teacher P
    - other approved teacher U
    - `links/T_S` accepted
    - `rooms/R` (teacherUid T, studentUids [S])
  - `userCanCreateOwnDoc`
    - S creates `users/S` role student → ok
    - creating `users/X` as S → fail
    - a teacher creating its own doc with `teacherStatus: "approved"` → fail; `"pending"` → ok
  - `cannotSelfApprove`: P updates own `teacherStatus` to approved → fail; updating `role` → fail.
  - `loginIdCannotBeTaken`
    - S creates `loginIds/abcd {uid: S}` → ok
    - U creates `loginIds/abcd` → fail
    - S updates or deletes `loginIds/abcd` → fail
  - `onlyApprovedTeacherSearches`
    - T `get` `loginIds/abcd` and `users/S` → ok
    - P → fail
    - S get `users/T` → fail
  - `linkLifecycle`
    - T creates `links/T_S2` pending with `teacherUid == T` → ok
    - P creates → fail
    - S2 updates status pending→accepted → ok
    - T updates status to accepted → fail
    - either party deletes → ok
  - `sessionsReadOnlyWhenLinked`
    - S creates `sessions/x {studentUid: S}` → ok
    - S creates with `studentUid: U` → fail
    - T reads → ok
    - U reads → fail
    - T query `where studentUid == S` → ok
  - `roomsOwnerOnly`
    - T creates a room with 6 studentUids → ok; with 7 → fail
    - P creates → fail
    - S reads `rooms/R` → ok
    - S updates → fail
  - `presence`
    - S writes `rooms/R/presence/S` → ok
    - S writes `rooms/R/presence/U` → fail
    - T reads → ok
    - U reads → fail
    - a student not in `studentUids` writes → fail
  - `proofPhotos` (storage)
    - S uploads `proofs/S/x.jpg` (image/jpeg, 100 KB) → ok
    - S uploads to `proofs/U/x.jpg` → fail
    - S uploads 6 MB → fail
    - T reads `proofs/S/x.jpg` → ok
    - U reads → fail (uses `firestore.get` on `links`)

- [ ] **Step 2: Run** `cd firebase && npm install && JAVA_HOME=/c/Users/10007409/.jdks/jbr-21.0.11 npm test`. Expected: FAIL (rules file deny-all / missing).

- [ ] **Step 3: Write `firestore.rules` and `storage.rules`** to make every case pass.
  - Helper functions: `signedIn()`, `me()`, `userDoc(uid)`, `isApprovedTeacher()`, `linkAccepted(teacherUid, studentUid)` = `get(/databases/$(database)/documents/links/$(teacherUid + '_' + studentUid)).data.status == 'accepted'`.
  - Students may read rooms whose `studentUids` contains them.
  - Teachers may read their own links and rooms (query `where teacherUid == me`). Students may read links where they are `studentUid`.

- [ ] **Step 4: Run** `npm test`. Expected: all rule tests PASS.

- [ ] **Step 5: Commit** `git add firebase .gitignore && git commit -m "feat(firebase): schema rules for users, links, rooms, presence, sessions, proofs with emulator tests"`

---

### Task 2: Report contract cases + Kotlin report functions

**Files:**
- Create: `contracts/report-cases.json`
- Modify: `shared/src/commonMain/kotlin/com/wjthinkbig/meetstudy/domain/Report.kt`
- Create: `shared/src/androidUnitTest/kotlin/com/wjthinkbig/meetstudy/domain/ReportContractTest.kt` (reads `../contracts/report-cases.json` via `java.io.File`, path relative to the `shared` project dir)

**Interfaces:**
- Produces in Kotlin `domain`:
  - `val LOST_STATES: Set<FocusState>` (the six states in Global Constraints)
  - `fun lostMs(segments: List<Segment>): Long`
  - `fun focusRatePct(segments: List<Segment>): Int` = round(focused ÷ total × 100), 0 if total is 0
  - `fun previousSession(all: List<SessionRecord>, r: SessionRecord): SessionRecord?`
  - `data class PlannerRow(val hour: Int, val minutes: List<FocusState?>)` with 60 entries
  - `fun plannerRows(segments: List<Segment>, tz: TimeZone): List<PlannerRow>`
    - Each minute takes the state of the segment containing that minute's `:30` instant, or null.
    - Rows run from the hour of the first segment's start to the hour containing `lastEnd - 1 ms`, in order.
    - Hours wrap 23 → 0.
  - `val SEOUL: TimeZone = TimeZone.of("Asia/Seoul")`
- Produces `contracts/report-cases.json`:
  - Shape: `{ "cases": [{ name, segments: [{state, startMs, endMs}], expect: { totalMs, focusedMs, focusRatePct, lostMs, planner: [{hour, row}] } }], "days": [{ name, today: "YYYY-MM-DD", days, records: [{id, startMs, segments, goalStar, proofStar}], expect: { daily: [{date, focusedMs, stars}], streak } }] }`
  - `row` is a 60-char string with codes: S STUDYING, A AWAY, L LOOKING_AWAY, C CAMERA_GAZE, D DROWSY, P PHONE, X APP_LEFT, B BREAK, `.` none.
  - All in Asia/Seoul.

- [ ] **Step 1: Write the contract file** with these cases:
  - `mockupSession`: the mockup's 14:10–15:18 KST session on 2026-11-13.
    - Segments (minutes after 14:00): 10–31 S, 31–34 L, 34–47 S, 47–55 B, 55–66 S, 66–67 L, 67–69 S, 69–72 A, 72–78 S.
    - Expected: totalMs 4 080 000, focusedMs 3 180 000, focusRatePct 78, lostMs 420 000.
    - Planner: rows for hours 14 and 15.
  - `crossesMidnight`: 23:50–00:20 KST STUDYING. Rows hours 23 and 0; the day counts on the start date.
  - `empty`: no segments → totals 0, focusRatePct 0, no rows.
  - `days` cases:
    - a 7-day window with a 4-day goal streak ending today
    - a streak broken yesterday but with a goal today → streak 1
    - no goal today but a goal yesterday → streak counts from yesterday (matches existing `streakDays`)

- [ ] **Step 2: Write `ReportContractTest`.** For each case it asserts `buildReport(...).totalMs/focusedMs`, `focusRatePct`, `lostMs`, and `plannerRows(..., SEOUL)` encoded to row strings. For each `days` case it asserts `dailyTotals(...)` and `streakDays(...)` with `SEOUL`.

- [ ] **Step 3: Run** `./gradlew :shared:testDebugUnitTest --tests '*ReportContractTest*'`. Expected: FAIL (functions missing).

- [ ] **Step 4: Implement** the Interfaces functions in `Report.kt`. Reuse the existing internal `localDate()` / `stars()` from `StarRules.kt` instead of duplicating them.

- [ ] **Step 5: Run** the full `:shared:testDebugUnitTest`. Expected: PASS, with existing tests still green.

- [ ] **Step 6: Commit** `feat(report): lost time, focus %, previous session, planner rows + cross-platform contract cases`

---

### Task 3: Teacher web scaffold, tokens, domain types, report maths (TS)

**Files:**
- Create: `teacherWeb/` via `npm create vite@7 teacherWeb -- --template react-ts`, then:
  - `teacherWeb/src/styles/tokens.css` (Global Constraints tokens and state colours as CSS variables; IBM Plex Sans KR from Google Fonts in `index.html`)
  - `teacherWeb/src/domain/types.ts`
  - `teacherWeb/src/domain/states.ts`
  - `teacherWeb/src/domain/report.ts`
  - `teacherWeb/src/domain/report.contract.test.ts`
  - `teacherWeb/src/domain/loginId.ts`
  - `teacherWeb/src/domain/loginId.test.ts`
- `teacherWeb/package.json` scripts: `dev`, `build`, `test` (`vitest run`), `typecheck`.

**Interfaces:**
- Produces `types.ts`:
  - `FocusState` (string union of the 8 names)
  - `Segment {state; startMs; endMs}`
  - `SessionDoc` and `RoomDoc` mirroring the schema
  - `Presence` (schema plus `updatedAtMs: number`)
  - `LinkDoc`
  - `UserDoc`
  - `TeacherStatus`
- Produces `states.ts`: `STATE_COLOR: Record<FocusState, string>`, `STATE_LABEL: Record<FocusState, string>`, `OFFLINE_COLOR`, `LOST_STATES`.
- Produces `report.ts`, mirroring Task 2:
  - `totalMs(s)`, `focusedMs(s)`, `lostMs(s)`, `focusRatePct(s)`
  - `plannerRows(s): {hour: number; minutes: (FocusState|null)[]}[]` (Asia/Seoul via `Intl.DateTimeFormat`)
  - `dailyTotals(records, todayYmd: string, days: number): {date: string; focusedMs: number; stars: number}[]`
  - `streakDays(records, todayYmd): number`
  - `previousSession(all, r)`
  - `seoulYmd(ms): string`
- Produces `loginId.ts`: `normalizeLoginId(raw): string`, `isValidLoginId(id): boolean`, `loginEmail(id): string`.

- [ ] **Step 1: Write `report.contract.test.ts`.** It imports `../../../contracts/report-cases.json` (enable `resolveJsonModule`) and asserts the same things as Task 2 Step 2. Write `loginId.test.ts`:
  - `normalizeLoginId(" Kim_01 ") === "kim_01"`
  - valid: `abcd`, `a_b_1234`
  - invalid: `abc`, 21 chars, `kim-01`, `김철수`
  - `loginEmail("kim_01") === "kim_01@id.meetstudy.app"`

- [ ] **Step 2: Run** `cd teacherWeb && npm test`. Expected: FAIL.

- [ ] **Step 3: Implement** `types.ts`, `states.ts`, `report.ts` and `loginId.ts`.

- [ ] **Step 4: Run** `npm test && npm run typecheck`. Expected: PASS.

- [ ] **Step 5: Commit** `feat(teacher-web): scaffold, design tokens, report maths matching the Kotlin contract`

---

### Task 4: Backend interface + mock backend

**Files:**
- Create: `teacherWeb/src/backend/Backend.ts`
- Create: `teacherWeb/src/backend/mock.ts`
- Create: `teacherWeb/src/backend/mock.test.ts`
- Create: `teacherWeb/src/backend/index.ts`

**Interfaces:**
- Produces `Backend`. Every `watch*` returns an unsubscribe function `() => void`.
  - **Auth**
    - `signIn(loginId, pw): Promise<void>`
    - `signUpTeacher(loginId, pw, name): Promise<void>` (throws `BackendError("id-taken" | "weak-password" | "invalid-id" | "network")`)
    - `signOut(): Promise<void>`
    - `watchMe(cb: (me: {uid; name; teacherStatus} | null) => void)`
  - **Links**
    - `findStudent(loginId): Promise<{uid; name; loginId} | null>`
    - `requestLink(student): Promise<void>`
    - `watchLinks(cb: (links: LinkDoc[]) => void)`
    - `unlink(studentUid): Promise<void>`
  - **Rooms**
    - `watchRooms(cb: (rooms: (RoomDoc & {id: string})[]) => void)`
    - `createRoom(input: {name; studentUids; awayThresholdSec; breakLimit}): Promise<string>`
    - `updateRoom(id, patch): Promise<void>`
    - `closeRoom(id): Promise<void>`
    - `watchPresence(roomId, cb: (p: Record<studentUid, Presence>) => void)`
  - **Sessions**
    - `listSessions(studentUid, sinceMs): Promise<(SessionDoc & {id})[]>`
    - `proofPhotoUrl(path): Promise<string | null>`
- `index.ts`: `export const backend: Backend`, chosen by `import.meta.env.VITE_BACKEND` (`"firebase"`, otherwise mock).
- Mock contents:
  - The mockup's teacher 김선생님 (approved)
  - Students 김하늘, 이준, 박서연, 최민, 정우, plus 4 more linked students
  - Rooms 중2 수학반 (5 students, open), 초6 영어반 (3, open), 주말 자습 (closed)
  - 14 days of sessions per student
  - Presence for open rooms advances every 3 s using an injected `now()` and timer: states rotate, `focusedSec` grows, segments append. 정우's `updatedAtMs` stays frozen, so the student goes offline.

- [ ] **Step 1: Write `mock.test.ts`** with fake timers.
  - `presenceAdvances`: after 3 s, at least one student's `segments` grew.
  - `frozenStudentGoesStale`: 정우's `updatedAtMs` < now − 60 000 after 61 s.
  - `createRoomLimitsSix`: 7 students → rejects with `BackendError("too-many")`.
  - `closeRoomMovesToClosed`
  - `signUpTeacherIdTaken`: an existing loginId → `id-taken`.

- [ ] **Step 2: Run** `npm test -- mock`. Expected: FAIL.

- [ ] **Step 3: Implement** `Backend.ts` (interface + `BackendError`), `mock.ts` and `index.ts`.

- [ ] **Step 4: Run** `npm test`. Expected: PASS.

- [ ] **Step 5: Commit** `feat(teacher-web): Backend interface and live mock backend`

---

### Task 5: Shared web components — Planner, PlannerStrip, Ring, StatusBadge

**Files:**
- Create: `teacherWeb/src/ui/Planner.tsx`, `PlannerStrip.tsx`, `FocusRing.tsx`, `StatusBadge.tsx`, `ui.test.tsx`, `ui.module.css` (or CSS next to each)

**Interfaces:**
- Consumes: `plannerRows`, `STATE_COLOR`, `STATE_LABEL`, `OFFLINE_COLOR` (Task 3).
- Produces:
  - `<Planner segments={Segment[]} />`
    - Full report planner per mockup `report.html`: a 44 px hour column + 6 ten-minute cells. Each cell is a rounded `#EEF2F1` box of 10 one-minute slivers.
    - Minute header 0/10/…/50 and a legend.
  - `<PlannerStrip segments={Segment[]} nowMs={number} />`: one row for the current Seoul hour, 6 cells, height 12 px, with labels "N시" / "N시 50분".
  - `<FocusRing pct={number} />`: 168 px, stroke 12, teal on #E3E8E6. Fills 0→pct over 900 ms easeOutCubic once on mount; with reduced motion it renders the final value at once.
  - `<StatusBadge state={FocusState | "offline"} detail?: string />`: pill with the state colour and white text, label `STATE_LABEL` or 오프라인, plus " · {detail}".

- [ ] **Step 1: Write `ui.test.tsx`.**
  - Planner renders 2 rows × 60 slivers for `mockupSession` (import from the contract). Minute 31 of row 14 has the LOOKING_AWAY colour.
  - The strip shows the current hour only.
  - The badge text for `{state: "BREAK", detail: "4분 남음"}` is "휴식 중 · 4분 남음".
  - The ring under `matchMedia(reduce)` shows "78%" immediately.

- [ ] **Step 2: Run.** Expected: FAIL.

- [ ] **Step 3: Implement** to match `report.html` visually (copy its CSS values).

- [ ] **Step 4: Run.** Expected: PASS.

- [ ] **Step 5: Commit** `feat(teacher-web): planner, strip, ring, badge components`

---

### Task 6: Report page (this session / weekly / monthly)

**Files:**
- Create: `teacherWeb/src/pages/ReportPage.tsx`, `teacherWeb/src/pages/ReportPage.test.tsx`

**Interfaces:**
- Consumes: `backend.listSessions`, `backend.proofPhotoUrl`, `report.ts`, the Task 5 components.
- Produces: route `/students/:uid/report/:sessionId?`. Without a `sessionId` it shows that student's latest session.
- **Layout** follows `report.html` exactly:
  - **Header:** "11월 13일 목요일의 공부" and "오후 2:10부터 3:18까지", plus tabs 이번 공부 / 주간 / 월간.
  - **Summary on paper:** ring, "N분 동안 집중했어요", goal line with stars, and three numbers (순공부 / 흐트러진 시간 / 쉬는 시간).
    - Each number has a comparison sentence vs `previousSession`: "지난번보다 N분 늘었어요" / "N분 줄었어요" / "지난번과 같아요". Omit it when there is no previous session.
    - Lost time going down is phrased as good (teal).
  - **Sheet "공부 시간표":** Planner and the "있었던 일" list (time, colour, label, duration).
  - **Side "이번 주":** 7 bars, today teal, ★ above goal days; weekly total; "하루 평균 N분 공부했어요"; "N일 연속 목표를 채웠어요" with the highlighter underline; stars; proof photo (`proofPhotoUrl`, or "사진이 아직 없어요").
- **Weekly / monthly tabs:** the same side block widened to 7 / 30 bars. Header "이번 주" / "최근 30일". Session list by date below; clicking a date opens that session.
- **Event labels:**
  - LOOKING_AWAY "다른 곳을 봤어요"
  - CAMERA_GAZE "화면을 오래 봤어요"
  - AWAY "자리를 비웠어요"
  - DROWSY "졸았어요"
  - PHONE "폰을 봤어요"
  - APP_LEFT "앱을 나갔어요"
  - BREAK "쉬었어요"

- [ ] **Step 1: Write tests** against the mock backend.
  - `showsMockupNumbers`: the session from the contract renders "53분 동안 집중했어요", "78%" (reduced motion) and "7분".
  - `noPreviousNoComparison`
  - `weeklyTabShowsSevenBars`
  - `photoMissingText`

- [ ] **Step 2: Run.** Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run.** Expected: PASS. Then run `npm run dev`, open the report in the browser at 1280 px and 390 px, screenshot, and compare with `report.html`.

- [ ] **Step 5: Commit** `feat(teacher-web): report page`

---

### Task 7: App shell + room page (first screen)

**Files:**
- Create: `teacherWeb/src/App.tsx` (router), `teacherWeb/src/pages/RoomPage.tsx`, `teacherWeb/src/pages/RoomPage.test.tsx`, `teacherWeb/src/ui/Nav.tsx`, `teacherWeb/src/domain/tile.ts`, `teacherWeb/src/domain/tile.test.ts`

**Interfaces:**
- Consumes: `backend.watchRooms`, `watchPresence`, `watchLinks`, `StatusBadge`, `PlannerStrip`.
- Produces `tile.ts`: `tileState(p: Presence | undefined, nowMs: number): {state: FocusState | "offline" | "waiting"; detail?: string}`.
  - `"waiting"` = invited, no presence yet. Shown with the 오프라인 colour and the text "아직 안 들어왔어요".
  - `offline` when `nowMs − updatedAtMs > 60 000`.
  - `detail`:
    - "N초째" / "N분째" for distracted states since `since`
    - "N분 남음" for BREAK (10-min break unless the room limit says otherwise)
- Routes:
  - `/` → redirects to the first open room
  - `/rooms/:id`
  - `/rooms/new`, `/rooms/:id/settings` (Task 8)
  - `/students` (Task 8)
  - `/students/:uid/report/:sessionId?`
  - `/login`, `/pending` (Task 9)
- **Room page** layout per `teacher-room.html`:
  - **Nav:**
    - who
    - 열린 방 / 닫힌 방 (count or date)
    - + 방 만들기
    - 학생 연결 관리 (pending count)
    - 학생 리포트
  - **Header:** name, one sentence "{elapsed}째 열려 있어요. 자리 비움은 {awayThresholdSec}초, 휴식은 {count}번까지 {minutes}분씩이에요."
  - **Actions:** 학생 초대 (primary), 방 설정, 방 닫기 (with a confirm dialog "방을 닫을까요? 공부 중인 학생은 혼자 공부로 이어서 해요.").
  - **Pulse line:** "N명 공부 중 · N명 딴짓 · N명 오프라인" (딴짓 = LOST_STATES minus AWAY; zero parts omitted).
  - **Grid:**
    - Tiles show: dark video placeholder "영상은 3단계에서 연결돼요"; name, badge, "N분 공부했어요 · 목표 N분"; PlannerStrip.
    - LOST_STATES-except-AWAY tiles get an orange inset border, AWAY a grey one, offline a dark-red placeholder "연결이 끊겼어요".
    - Columns: 3, ≤1100 px 2, ≤760 px 1 (nav becomes a horizontal scroller).
    - Empty slots: one dashed "+ 학생 초대하기 (N자리 남음)" tile when fewer than 6.
  - Clicking a tile → that student's report.
  - Closed room: read-only header "닫힌 방이에요", no grid, and a list of who joined with links to their reports.

- [ ] **Step 1: Write tests.**
  - `tile.test.ts`:
    - `offlineAfter60s` (updatedAt 61 s ago → offline)
    - `waitingWhenNoPresence`
    - `distractedDetailSeconds` (since 12 s ago → "12초째")
    - `breakRemaining`
  - `RoomPage.test.tsx`: renders 5 tiles + 1 empty slot for 중2 수학반 from mock; the pulse text; close-room confirm calls `closeRoom`.

- [ ] **Step 2: Run.** Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run.** Expected: PASS. Then run `npm run dev`, screenshot at 1366 px, 1024 px and 390 px, and compare with `teacher-room.html`.

- [ ] **Step 5: Commit** `feat(teacher-web): app shell and live room page`

---

### Task 8: Room form, student links page, student report list

**Files:**
- Create: `teacherWeb/src/pages/RoomFormPage.tsx` (used for both new and settings)
- Create: `teacherWeb/src/pages/StudentsPage.tsx`
- Create: tests for both

**Interfaces:**
- Consumes: `createRoom`, `updateRoom`, `findStudent`, `requestLink`, `watchLinks`, `unlink`.
- **Room form** (same page for new and settings):
  - Room name, required, up to 20 chars.
  - Linked students as checkable chips; at most 6, with "6명까지 고를 수 있어요" when hit.
  - Away seconds: segmented choice 10 / 15 / 20 / 30, default 10.
  - Breaks: count stepper 0–5, default 3; minutes 5 / 10 / 15, default 10.
  - Buttons: "방 만들기" or "저장하기".
  - Removing a student in settings is the spec's 내보내기.
- **Students page:**
  - Search box "학생 아이디" + "찾기".
    - Results: "{name} ({loginId})" + "연결 요청 보내기".
    - Not found: "그 아이디의 학생이 없어요".
  - Lists: 연결된 학생 (with 리포트 보기 and 연결 끊기 + confirm) and 기다리는 요청.

- [ ] **Step 1: Write tests.**
  - `formLimitsSix`
  - `formDefaults` (10 s, 3 × 10)
  - `settingsRemovesStudent` (calls `updateRoom` with `studentUids` without them)
  - `searchNotFoundText`
  - `requestShowsPending`

- [ ] **Step 2: Run.** Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run.** Expected: PASS.

- [ ] **Step 5: Commit** `feat(teacher-web): room form and student links`

---

### Task 9: Login / sign-up / pending screens + Firebase backend

**Files:**
- Create: `teacherWeb/src/pages/LoginPage.tsx`, `teacherWeb/src/pages/PendingPage.tsx`
- Create: `teacherWeb/src/backend/firebase.ts`, `teacherWeb/src/backend/firebase.emulator.test.ts`
- Create: `teacherWeb/.env.example` (keys `VITE_BACKEND`, `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_STORAGE_BUCKET`, `VITE_FIREBASE_APP_ID`, `VITE_USE_EMULATORS`)

**Interfaces:**
- Implements `Backend` (Task 4) with Firebase JS SDK 12, against the schema.
  - **Sign-up** (via `normalizeLoginId` / `loginEmail`):
    1. `createUserWithEmailAndPassword`
    2. transaction-create `loginIds/{id}`; if it exists, delete the just-created auth user and throw `id-taken`
    3. set `users/{uid}` role teacher, `teacherStatus: "pending"`
  - `watchMe` subscribes to `users/{uid}`, so approval flips the UI live.
  - Presence `updatedAt` serverTimestamp → `updatedAtMs`.
  - Emulator connect when `VITE_USE_EMULATORS=1`.
- **Guards** in `App.tsx`:
  - signed out → `/login`
  - pending → `/pending`, showing "승인을 기다리고 있어요. 승인되면 이 화면이 자동으로 바뀌어요."
- **Login page:** 로그인 / 선생님 가입 tabs. Fields: 아이디, 비밀번호, and 이름 for sign-up. Error texts:
  - id-taken "이미 쓰는 아이디예요"
  - wrong password "아이디나 비밀번호가 맞지 않아요"
  - invalid-id "아이디는 영어 소문자, 숫자, _로 4~20자예요"

- [ ] **Step 1: Write `firebase.emulator.test.ts`** (runs only when `FIREBASE_EMULATORS=1`, using `firebase emulators:exec --only auth,firestore,storage --project demo-meetstudy`, from `firebase/`).
  - `signUpCreatesPendingTeacher`
  - `signUpIdTakenLeavesNoUser`
  - `approvedTeacherFindsAndLinksStudent` (seed a student via the admin path)
  - `createRoomAndWatchPresence` (the seeded student writes presence; `watchPresence` fires with `updatedAtMs` set)
- Add script `"test:emulator": "cd ../firebase && firebase emulators:exec --only auth,firestore,storage --project demo-meetstudy \"cd ../teacherWeb && FIREBASE_EMULATORS=1 vitest run firebase.emulator\""`.

- [ ] **Step 2: Run** `npm run test:emulator`. Expected: FAIL.

- [ ] **Step 3: Implement** `firebase.ts`, the login/pending pages and the guards.

- [ ] **Step 4: Run** `npm test && npm run test:emulator && npm run build`. Expected: PASS and `dist/` built.

- [ ] **Step 5: Commit** `feat(teacher-web): firebase backend, login, approval gate`

---

### Task 10: Student app report redesign (Compose)

**Files:**
- Add fonts: `shared/src/commonMain/composeResources/font/ibmplexsanskr_regular.ttf`, `ibmplexsanskr_semibold.ttf`, `ibmplexsanskr_bold.ttf` (download from `https://github.com/google/fonts/raw/main/ofl/ibmplexsanskr/IBMPlexSansKR-{Regular,SemiBold,Bold}.ttf`; OFL, add `OFL.txt` alongside)
- Create: `shared/src/commonMain/kotlin/com/wjthinkbig/meetstudy/ui/report/Planner.kt`, `FocusRing.kt`, `ReportTokens.kt`
- Modify: `shared/src/commonMain/kotlin/com/wjthinkbig/meetstudy/ui/screens/ReportScreen.kt` (rewrite the UI and keep `formatDuration` and `eventLine`; `ReportFormatTest` must still pass)
- Remove usages of `TimelineBar` / `TrendChart` in the report. Delete those files if they are unused elsewhere (grep first).
- Test: `shared/src/commonTest/kotlin/com/wjthinkbig/meetstudy/ui/report/ReportCopyTest.kt`

**Interfaces:**
- Consumes: Task 2's `plannerRows`, `lostMs`, `focusRatePct`, `previousSession`, `SEOUL`.
- Produces:
  - `fun comparisonText(curMs: Long, prevMs: Long?, lowerIsBetter: Boolean): Pair<String, Boolean>?`: text plus whether it is good, or null with no previous session.
    - Texts: "지난번보다 N분 늘었어요" / "N분 줄었어요" / "지난번과 같아요". Minutes are rounded.
  - `fun headerTitle(startMs: Long): String` → "11월 13일 목요일의 공부"
  - `fun timeRange(startMs, endMs): String` → "오후 2:10부터 3:18까지"
  - All in Asia/Seoul.
- `ReportScreen(record, allRecords, tz, today, onBack)` keeps its signature. Callers stay unchanged, but use `SEOUL` internally.
- **Tabs:** keep 이번 공부 / 주간 / 월간. 주간 and 월간 use the same side block widened to 7 / 30 bars, like Task 6.
- **Layout** per `report.html`, using `AdaptivePane` rules:
  - Wide: summary row, then planner sheet (weight 1.65) + side (weight 1).
  - Portrait: stacked in a vertical scroll. Reports may scroll; the one-screen rule was for the guide.
  - The ring animates once with `animateFloatAsState`, honouring the system animator scale.

- [ ] **Step 1: Write `ReportCopyTest`.**
  - `comparisonText(53*60_000, 41*60_000, false) == "지난번보다 12분 늘었어요" to true`
  - lost time 7 vs 9 min, `lowerIsBetter` → `"2분 줄었어요" to true`
  - prev null → null
  - `headerTitle` / `timeRange` for 2026-11-13 14:10–15:18 KST

- [ ] **Step 2: Run.** Expected: FAIL.

- [ ] **Step 3: Implement** the tokens, the font family (`FontFamily(Font(Res.font.ibmplexsanskr_regular), …)`), `Planner` (Canvas or Rows of 10 slivers; same cell geometry as the web), `FocusRing`, and the screen.

- [ ] **Step 4: Run** the full `:shared:testDebugUnitTest` and `:studentApp:assembleDebug`. Expected: PASS. Then install on the connected device, open 내 기록, and screenshot (`adb exec-out screencap -p`).

- [ ] **Step 5: Commit** `feat(student): redesigned report with study planner`

---

### Task 11: Cloud interface, Android Firebase implementation, login/sign-up

**Files:**
- Modify: `gradle/libs.versions.toml`:
  - plugin `googleServices` = `com.google.gms.google-services` 4.4.x
  - libs `firebase-bom` 34.x, `firebase-auth`, `firebase-firestore`, `firebase-storage`, `kotlinx-coroutines-play-services` 1.10.2
- Modify: `build.gradle.kts` (root, `alias(libs.plugins.googleServices) apply false`)
- Modify: `studentApp/build.gradle.kts`: `if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")`
- Modify: `shared/build.gradle.kts` (androidMain deps)
- Create: `shared/src/commonMain/kotlin/com/wjthinkbig/meetstudy/data/Cloud.kt`
- Create: `shared/src/commonMain/kotlin/com/wjthinkbig/meetstudy/data/LoginIds.kt`
- Create: `shared/src/androidMain/kotlin/com/wjthinkbig/meetstudy/data/FirebaseCloud.kt`
- Create: `shared/src/iosMain/.../data/Cloud.ios.kt` (local-only)
- Create: `shared/src/commonMain/kotlin/com/wjthinkbig/meetstudy/ui/screens/LoginScreen.kt`
- Modify: `ui/App.kt`
- Tests:
  - `shared/src/commonTest/.../data/LoginIdsTest.kt`
  - `shared/src/commonTest/.../ui/screens/LoginFlowTest.kt` (with a fake Cloud)

**Interfaces:**
- Produces `Cloud.kt`:
  - `data class CloudUser(val uid: String, val loginId: String, val name: String)`
  - `sealed class CloudError(msg) : Exception` with `IdTaken`, `WrongPassword`, `InvalidId`, `WeakPassword`, `Network`
  - `data class LinkRequest(val teacherUid: String, val teacherName: String, val accepted: Boolean)`
  - `data class RoomInfo(val id: String, val name: String, val teacherName: String, val awayThresholdSec: Int, val breakCount: Int, val breakMinutes: Int)`
  - `data class PresenceUpdate(val state: FocusState, val sinceMs: Long, val focusedSec: Long, val goalSec: Long, val sessionStartMs: Long, val segments: List<Segment>)` (epoch ms)
  - `interface Cloud`:
    - `val available: Boolean`
    - `fun currentUser(): CloudUser?`
    - `suspend fun signIn(loginId: String, password: String): CloudUser`
    - `suspend fun signUpStudent(loginId: String, password: String, name: String): CloudUser`
    - `fun signOut()`
    - `fun linkRequests(): Flow<List<LinkRequest>>`
    - `suspend fun answerLink(teacherUid: String, accept: Boolean)` (reject = delete)
    - `suspend fun unlink(teacherUid: String)`
    - `fun openRooms(): Flow<List<RoomInfo>>` (`studentUids` array-contains me, `status == open`)
    - `suspend fun publishPresence(roomId: String, p: PresenceUpdate)`
    - `suspend fun uploadSession(r: SessionRecord, roomId: String?)` (set by id; idempotent)
    - `suspend fun uploadProof(sessionId: String, localPath: String): String` (returns the storage path; also sets `sessions/{id}.proofPhotoPath`)
  - `expect fun cloud(): Cloud` (a singleton). Android returns `FirebaseCloud` when `FirebaseApp.getApps(ctx)` is non-empty, else `LocalOnlyCloud` (`available = false`, every call throws `CloudError.Network`). Use the same application-context holder `appFilesDir()` already uses.
- `LoginIds.kt`: `normalizeLoginId`, `isValidLoginId`, `loginEmail`, identical in behaviour to Task 3.
- **Sign-up:** same sequence as Task 9, with role student and no `teacherStatus`. On `loginIds` conflict, delete the auth user and throw `IdTaken`.
- **App gate:**
  - `cloud().available && currentUser() == null` → `LoginScreen` before enroll.
  - Not available → today's flow, unchanged.
  - Home shows the signed-in name and a 로그아웃 TextButton when available.
- **LoginScreen:** 로그인 / 가입 tabs; fields 아이디, 비밀번호, 이름 (sign-up). Error copy is the same as Task 9. Use `BigButton`. One screen, no scroll, `AdaptivePane` layout.

- [ ] **Step 1: Write tests.**
  - `LoginIdsTest`: the same cases as Task 3 Step 1.
  - `LoginFlowTest`:
    - `SignUpFailsCleanlyWhenIdTaken`: a fake Cloud throws `IdTaken` → `loginErrorText(e) == "이미 쓰는 아이디예요"`.
    - `invalidIdBlockedBeforeNetwork`: the fake records no calls.
  - Expose `internal fun loginErrorText(e: Throwable): String` for these tests.

- [ ] **Step 2: Run.** Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `:shared:testDebugUnitTest :studentApp:assembleDebug` **without** `google-services.json`. Expected: PASS, and the app launches into today's flow (local-only).

- [ ] **Step 5: Commit** `feat(student): Cloud interface, Firebase implementation, login and sign-up`

---

### Task 12: Link requests, open rooms, joining a room

**Files:**
- Modify: `ui/App.kt` (Home: 선생님 연결 요청 card and 참여할 수 있는 방 list, shown only when `cloud().available`)
- Modify: `ui/screens/StudyFlow.kt`, `ui/screens/StudyScreen.kt`
- Test: `shared/src/commonTest/.../ui/screens/RoomRulesTest.kt`

**Interfaces:**
- Consumes: `Cloud.linkRequests`, `answerLink`, `unlink`, `openRooms`, `RoomInfo`.
- Produces:
  - `fun StudyFlow(room: RoomInfo?, onExit: () -> Unit)`
  - `StudyScreen(source, goalMs, baseline, room: RoomInfo?, onFinished)`
  - `internal fun detectionConfigFor(room: RoomInfo?): DetectionConfig` (`awayMs = awayThresholdSec * 1000`, default when null)
  - `internal fun breakPolicyFor(room: RoomInfo?): BreakPolicy` (count and minutes, default when null)
- **Home UI:**
  - Requests show "{teacherName} 선생님이 연결을 원해요" with 수락 / 거절.
  - Accepted teachers are listed under "연결된 선생님" with 연결 끊기 + confirm.
  - Rooms show "{name} · {teacherName} 선생님" with 들어가기 → `StudyFlow(room)`.
  - The existing 공부 시작하기 stays solo (`room = null`).

- [ ] **Step 1: Write `RoomRulesTest`.**
  - `defaultsWhenSolo` (10 000 ms, 3 × 10)
  - `roomOverridesAway` (20 s → 20 000)
  - `roomOverridesBreaks` (2 × 5)

- [ ] **Step 2: Run.** Expected: FAIL.

- [ ] **Step 3: Implement.** Pass the config into `FocusStateMachine(baseline, config = …)` and the break policy into `StudySession`. Check `StudySession`'s constructor and thread `BreakPolicy` through it if it isn't yet a parameter.

- [ ] **Step 4: Run** the tests and build. Expected: PASS.

- [ ] **Step 5: Commit** `feat(student): accept teacher links and join rooms with room rules`

---

### Task 13: Presence publishing + session/photo upload queue

**Files:**
- Create: `shared/src/commonMain/kotlin/com/wjthinkbig/meetstudy/data/PresencePublisher.kt`
- Create: `shared/src/commonMain/kotlin/com/wjthinkbig/meetstudy/data/UploadQueue.kt`
- Modify: `StudyScreen.kt` (publisher when `room != null`), `StudyFlow.kt` (enqueue after save and after the proof photo), `App.kt` (flush on start)
- Tests: `shared/src/commonTest/.../data/PresencePublisherTest.kt`, `UploadQueueTest.kt`

**Interfaces:**
- `class PresencePublisher(private val send: suspend (PresenceUpdate) -> Unit, private val heartbeatMs: Long = 30_000)`
  - `fun onChange(p: PresenceUpdate)`: sends immediately when `state` differs from the last sent.
  - `fun onTick(nowMs: Long, p: PresenceUpdate)`: sends when `nowMs − lastSentMs ≥ heartbeatMs`.
  - Send failures are swallowed and retried at the next tick. A denied write (kicked or room closed) stops publishing: `stopped = true`.
- `@Serializable data class PendingUpload(val sessionId: String, val roomId: String?, val photoPath: String?, val sessionDone: Boolean, val photoDone: Boolean)`
- `class UploadQueue(private val dir: String, private val cloud: Cloud, private val records: () -> List<SessionRecord>)`
  - `fun enqueue(sessionId: String, roomId: String?, photoPath: String?)`: persists to `$dir/uploads.json`, merging by id.
  - `suspend fun flush()`: for each item, upload the session if not done, then the photo if present and not done. Each step is marked done independently. A photo failure never resets `sessionDone`.
- **Wiring:**
  - `StudyScreen` builds `PresenceUpdate` from `session.state`, `segments so far`, `focusedMs` and the goal, converted to epoch ms with the existing `toEpoch` offset.
  - `onChange` on every state change; `onTick` from the existing 1 s loop.
  - `StudyFlow` enqueues after the record is saved and again when the proof photo is saved, then calls `flush()` in a coroutine.
  - `App` calls `flush()` on start when available.

- [ ] **Step 1: Write tests** with a fake Cloud and a temp dir.
  - `PresencePublisherTest`:
    - `sendsOnStateChange`
    - `heartbeatEvery30s` (ticks at 0, 29 s → 1 send; 30 s → 2)
    - `retriesAfterFailure`
    - `stopsWhenDenied` (the fake throws `CloudError.Network("permission-denied")` → no more sends)
  - `UploadQueueTest`:
    - `offlineThenOnlineUploadsOnce`: the first flush fails → the next succeeds → `uploadSession` called with the same id exactly once successfully.
    - `photoFailureDoesNotBlockSession`
    - `survivesRestart`: a new `UploadQueue` on the same dir sees pending items.

- [ ] **Step 2: Run.** Expected: FAIL.

- [ ] **Step 3: Implement and wire.**

- [ ] **Step 4: Run** the full tests and build. Expected: PASS.

- [ ] **Step 5: Commit** `feat(student): live presence and reliable session/photo upload`

---

### Task 14: Docs, deploy, end-to-end check with the user's project

**Files:**
- Modify: `docs/요건정의서.md`
  - Section 2: 선생님 = 웹
  - Section 4.1: this round id/password only
  - Section 6: the schema block replaced by this plan's schema
  - Section 6.1: support floors — Chrome/Edge 107+, Safari 16+, Android 8.0+ (권장 10+), iOS 15.5+
- Create: `docs/firebase-setup.md`, the user's checklist:
  1. Create the project, Firestore in asia-northeast3.
  2. Switch to Blaze and create the Storage bucket.
  3. Enable Auth email/password.
  4. Register the Android app `com.wjthinkbig.meetstudy.student` → put `google-services.json` in `studentApp/`.
  5. Register the web app → copy the values into `teacherWeb/.env.local` with `VITE_BACKEND=firebase`.
  6. `cd firebase && npx firebase-tools login && npx firebase-tools use --add`.
  7. `npx firebase-tools deploy --only firestore:rules,storage,hosting` (after `cd ../teacherWeb && npm run build`).
  8. Approve a teacher: in the Firestore console set `users/{uid}.teacherStatus = "approved"`.

- [ ] **Step 1: Write the docs.**

- [ ] **Step 2: Mock smoke test (no keys).**
  - Run `cd teacherWeb && npm run build && npm test`.
  - `npm run dev`: walk login(mock) → room → tile → report → back → students → new room. Screenshot each.

- [ ] **Step 3: Real project check** (only after the user confirms the two config files are in place).
  - **Teacher web:** sign up as a teacher (web), approve it in the console, and confirm `/pending` flips by itself.
  - **Student app on the device:** sign up as a student and install.
  - **Linking:** the teacher searches the student id and sends a request; the student accepts on Home.
  - **Room:** the teacher creates a room with that student; the student joins and studies 2 minutes.
  - **Live status:** the tile shows 공부 중. When the student covers the camera, the tile shows 자리 비움 within about 2 s of the student screen (stopwatch or screen recording on both).
  - **Session and photo:** end the session, take the proof photo, and confirm the teacher report shows the session and the photo.

- [ ] **Step 4: Commit** `docs: firebase setup, requirements updated for teacher web`
