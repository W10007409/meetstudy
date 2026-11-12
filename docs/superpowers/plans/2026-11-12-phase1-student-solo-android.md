# MeetStudy 1단계 – 학생 혼자 모드 (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 학생 혼자 공부 모드를 Android에서 끝까지 동작시킨다. 흐름은 얼굴 등록 → 본인 확인 → 거치 안내 애니메이션 → 기준 자세 → 감지·타이머·휴식 → 목표 달성과 별 → 리포트 → 문제집 자동 촬영 인증 → 공유 카드 순서다. 서버 키 없이 로컬에서 동작해야 한다.

**Architecture:** KMP 모듈 `shared`에 순수 Kotlin 도메인과 Compose Multiplatform UI를 둔다. 도메인은 상태 머신, 세션 타이머, 리포트, 별 규칙이다. 카메라와 ML은 `FrameSignals`라는 플랫폼 중립 신호로 바꿔서 도메인에 넣는다. 그래서 감지 로직은 전부 `commonTest`에서 단위 테스트할 수 있다. Android 구현(CameraX, ML Kit, LiteRT, MediaPipe, OpenCV)은 `androidMain`에 두고, iOS는 같은 인터페이스의 `TODO()` 스텁만 둔다.

**Tech Stack:**
- Kotlin 2.2.21, AGP 8.13.2, Gradle 8.14.3 (wrapper), Compose Multiplatform 1.10.0
- kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime
- CameraX 1.4.1
- ML Kit: face-detection, pose-detection, text-recognition-korean 16.0.1
- LiteRT(TFLite) MobileFaceNet, MediaPipe tasks-vision EfficientDet-Lite0, OpenCV 4.10.0

**Spec:** [docs/요건정의서.md](../../요건정의서.md)

## Global Constraints

- 패키지: `com.wjthinkbig.meetstudy.student` (앱), 공유 코드 네임스페이스 `com.wjthinkbig.meetstudy`
- minSdk 26 (Android 8.0), compile/targetSdk 36. JDK는 Android Studio JBR(`C:\Program Files\Android\Android Studio\jbr`)을 쓴다.
- 얼굴 임베딩은 기기 밖으로 나가지 않는다. 원본 얼굴 사진은 저장하지 않는다. 영상 녹화는 하지 않는다.
- 감지 분석은 약 5fps, 상체 감지는 얼굴이 없을 때만 약 2fps로 돌린다.
- 상태 색상(학생앱·선생님앱 공통):
  - 공부 초록 `#4CC38A`, 자리비움 회색 `#9AA3AE`, 딴짓 주황 `#FF9F43`, 휴식 파랑 `#5AA9FF`, 오프라인 빨강 `#FF6B6B`
- 판정 지속 기준:
  - 자리 비움 10초(혼자 모드), 고개 돌림 15초(|yaw-기준| > 35°, 또는 pitch가 기준보다 25° 넘게 위), 졸음/엎드림 20초(양 눈 뜸 확률 < 0.3, 또는 상체만 보이고 코가 어깨선 아래), 폰 사용 10초
  - 앱 이탈은 즉시, 본인 재확인은 30초마다
  - 고개 숙임은 기준 pitch에서 아래로 40°까지 '공부 중'
- 판정 전 대기 시간은 소급해서 차감한다. 이벤트 시작 시각 = 조건이 처음 시작된 시각.
- 휴식(혼자 모드 기본값): 3회, 회당 10분. 한도를 넘기면 자리 비움으로 전환한다.
- 별: 목표 달성 1개 + 인증 1개, 하루 최대 2개. 중도 종료하면 인증분만 받는다.
- 인증 촬영: 사각형 검출 + OCR 10자 이상이 **1초 유지**되면 자동 촬영. 셔터 버튼은 없다.
- UI 문구는 한국어이고 초등학생도 읽을 수 있게 짧게 쓴다. 톤은 밝고 캐주얼하게, 둥근 카드를 쓴다.
- 로그인, Firebase, LiveKit은 이번 범위가 아니다. 키가 없어도 빌드되고 실행되어야 한다.

## Review Focus

1. **상태가 깜빡임**: 신호가 경계에서 오락가락할 때(예: yaw 34°↔36°) 상태가 요동치면 안 된다. 공부 중으로 돌아올 때는 2초 동안 조건이 계속 해소되어야 한다 → Task 2 테스트 `returnToStudyingNeedsTwoSecondsOfClearSignal`
2. **고개 숙여 문제 풀기**: 얼굴이 사라져도 상체가 기준 위치에 있으면 자리 비움이 되면 안 된다 → Task 2 `faceLostButPersonPresentStaysStudying`
3. **휴식 중 자리를 떠남**: 휴식 중에는 자리 비움 판정이 멈춰야 하고, 한도 시간을 넘기면 그 시점부터 자리 비움이 되어야 한다 → Task 3 `breakOverrunBecomesAway`
4. **자정을 넘긴 세션 / 하루 별 상한**: 별 상한은 세션 시작일 기준으로 센다. 같은 날 두 번째 세션의 목표 달성 별은 상한 때문에 0개가 되어야 한다 → Task 4 `dailyCapCountsAcrossSessions`
5. **앱을 나갔다가 돌아옴**: 백그라운드로 간 동안은 APP_LEFT, 돌아오면 다시 캘리브레이션 없이 재개해야 한다. 카메라도 다시 붙어야 한다 → Task 3 `appLeftPausesAndResumes` + Task 8 기기 확인

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew*
shared/build.gradle.kts
shared/src/commonMain/kotlin/com/wjthinkbig/meetstudy/
  domain/FrameSignals.kt        // 프레임 신호, Baseline
  domain/FocusState.kt          // 상태 enum, 전이 이벤트
  domain/FocusStateMachine.kt   // 순수 판정 로직
  domain/DetectionConfig.kt     // 임계값 상수
  domain/StudySession.kt        // 타이머/휴식/목표/이벤트 로그
  domain/Report.kt              // 리포트 계산, 추이
  domain/StarRules.kt
  domain/FaceMath.kt            // 코사인 유사도, 평균 임베딩
  domain/BaselineCalibrator.kt
  data/SessionRecord.kt, data/SessionStore.kt (expect 파일 경로 + JSON)
  platform/FrameSource.kt       // expect: 카메라→FrameSignals Flow, 미리보기 composable
  platform/FaceVault.kt         // expect: 임베딩 암호화 저장
  platform/ProofCamera.kt       // expect: 문제집 자동 촬영 composable
  platform/Share.kt             // expect: 이미지 공유
  ui/App.kt (화면 상태 머신), ui/theme/*, ui/screens/*.kt, ui/guide/PlacementGuideAnimation.kt
shared/src/commonTest/kotlin/... // 도메인 테스트
shared/src/androidMain/kotlin/... // CameraX, ML Kit, LiteRT, MediaPipe, OpenCV 구현
shared/src/iosMain/kotlin/...     // TODO() 스텁
shared/src/androidMain/assets/    // mobile_face_net.tflite, efficientdet_lite0.tflite
studentApp/ (Android application: MainActivity, Manifest, FileProvider)
```

화면 이동은 `App.kt` 안의 sealed `Screen` 상태로 처리한다. 내비게이션 라이브러리는 쓰지 않는다.

---

### Task 1: 프로젝트 골격 + 기기 실행

**Files:**
- Create: 루트 Gradle 파일, `gradle/libs.versions.toml`, `shared/build.gradle.kts`, `studentApp/build.gradle.kts`, `studentApp/src/main/AndroidManifest.xml`, `studentApp/src/main/kotlin/com/wjthinkbig/meetstudy/student/MainActivity.kt`, `shared/src/commonMain/.../ui/App.kt`, `.gitignore`
- Test: `shared/src/commonTest/kotlin/com/wjthinkbig/meetstudy/SmokeTest.kt`

**Interfaces:**
- Produces: `@Composable fun App()` (commonMain). `MainActivity`가 `setContent { App() }`를 호출한다.

- [ ] **Step 1:** `git init`, `.gitignore`(build, .gradle, local.properties, .idea, *.iml)
- [ ] **Step 2:** 캐시된 `~/.gradle/wrapper/dists/gradle-8.14.3-bin/*/gradle-8.14.3/bin/gradle.bat wrapper --gradle-version 8.14.3`로 wrapper를 만든다. `JAVA_HOME`에는 JBR 경로를 쓴다.
- [ ] **Step 3:** 버전 카탈로그와 모듈을 구성한다.
  - `shared`: kotlin multiplatform + compose + android library. 타깃은 androidTarget, iosArm64, iosSimulatorArm64.
  - `studentApp`: android application. `local.properties`에 `sdk.dir`을 넣는다.
- [ ] **Step 4:** `SmokeTest`: `assertEquals(2, 1 + 1)`을 쓴다. `./gradlew :shared:testDebugUnitTest` 실행 결과 PASS
- [ ] **Step 5:** `App()`은 "MeetStudy" 텍스트를 띄운다. `./gradlew :studentApp:installDebug` 후 `adb -s R8YW70BEYLZ shell am start -n com.wjthinkbig.meetstudy.student/.MainActivity`로 실행해서 화면에 텍스트가 보이면 통과
- [ ] **Step 6:** Commit `chore: KMP+CMP skeleton`

### Task 2: FrameSignals + FocusStateMachine (핵심 판정)

**Files:**
- Create: `domain/FrameSignals.kt`, `domain/FocusState.kt`, `domain/DetectionConfig.kt`, `domain/FocusStateMachine.kt`
- Test: `commonTest/.../domain/FocusStateMachineTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class FrameSignals(
  val timeMs: Long,
  val faceVisible: Boolean,
  val identityMatch: Boolean?,     // null = 이번 프레임은 확인 안 함
  val yaw: Float?, val pitch: Float?,   // 도, pitch 음수 = 아래
  val eyesOpenProb: Float?,        // 양 눈 중 큰 값
  val personVisible: Boolean,      // 상체 감지(얼굴 없을 때만 의미)
  val noseBelowShoulders: Boolean?,// 엎드림 신호
  val phoneVisible: Boolean,
)
data class Baseline(val yaw: Float, val pitch: Float)
enum class FocusState { STUDYING, AWAY, LOOKING_AWAY, DROWSY, PHONE, APP_LEFT, BREAK }
data class StateChange(val state: FocusState, val startedAtMs: Long)
data class DetectionConfig(
  val awayMs: Long = 10_000, val lookAwayMs: Long = 15_000, val drowsyMs: Long = 20_000, val phoneMs: Long = 10_000,
  val yawLimitDeg: Float = 35f, val pitchUpLimitDeg: Float = 25f, val pitchDownAllowDeg: Float = 40f,
  val eyesClosedProb: Float = 0.3f, val returnDebounceMs: Long = 2_000,
)
class FocusStateMachine(baseline: Baseline, config: DetectionConfig = DetectionConfig()) {
  val state: FocusState
  fun onFrame(s: FrameSignals): StateChange?   // 상태가 바뀐 프레임에서만 non-null
  fun setAppLeft(left: Boolean, timeMs: Long): StateChange?
  fun setBreak(onBreak: Boolean, timeMs: Long): StateChange?
}
```
- 규칙:
  - 프레임마다 "후보 상태"를 고른다. 우선순위는 AWAY > PHONE > DROWSY > LOOKING_AWAY > STUDYING이다.
  - AWAY 후보는 `!faceVisible && !personVisible`이거나 `identityMatch == false`이다.
  - DROWSY 후보는 `eyesOpenProb < 0.3`이거나 `(!faceVisible && personVisible && noseBelowShoulders == true)`이다.
  - 후보가 처음 나타난 시각을 기억해 두고, 각 지속 기준을 채우면 `StateChange(candidate, firstSeenAt)`로 전이한다(소급).
  - STUDYING으로 돌아갈 때는 2초 동안 해소 조건이 계속되어야 하고, `startedAtMs`는 해소가 시작된 시각이다.
  - APP_LEFT와 BREAK는 즉시 전이하고 프레임 판정보다 우선한다. 해제되면 STUDYING(시각 = 해제 시각)으로 간다.

- [ ] **Step 1: 실패하는 테스트 작성** — 헬퍼 `studying(t)`, `noFace(t, person=false)` 등으로 프레임 시퀀스를 만든다.
  - `awayAfterTenSecondsIsRetroactive`: t=0~9.9s 얼굴·사람 없음 → null, t=10s → `StateChange(AWAY, 0)`
  - `faceLostButPersonPresentStaysStudying`: 30초 동안 face=false, person=true, noseBelow=false → 모든 프레임 null, `state == STUDYING`
  - `lookingDownWithinFortyDegreesIsStudying`: pitch = baseline-39 동안 30초 → null
  - `yawBeyondLimitFor15sIsLookingAway`: yaw = base+36 → 15s에 `LOOKING_AWAY@0`, 14.9s에는 null
  - `eyesClosed20sIsDrowsy`, `headDownOnDesk20sIsDrowsy`, `phone10sIsPhone`
  - `identityMismatchCountsAsAway`: identityMatch=false 10초 → AWAY
  - `returnToStudyingNeedsTwoSecondsOfClearSignal`: AWAY 상태에서 clear 1.9초 후 다시 없음 → STUDYING 전이 없음. clear가 2초 지속되면 `STUDYING@clearStart`
  - `appLeftIsImmediateAndOverrides`, `breakSuppressesDetection`: BREAK 중 얼굴 없음 30초 → null
- [ ] **Step 2:** `./gradlew :shared:allTests --tests "*FocusStateMachineTest*"` → FAIL(컴파일 에러)
- [ ] **Step 3:** 위 시그니처대로 구현한다(타이머 없이 `timeMs`만 사용하는 순수 로직).
- [ ] **Step 4:** 테스트 실행 → PASS
- [ ] **Step 5:** Commit `feat(domain): focus state machine`

### Task 3: StudySession (타이머·휴식·목표·소급 차감)

**Files:** Create `domain/StudySession.kt`; Test `commonTest/.../domain/StudySessionTest.kt`

**Interfaces:**
- Consumes: `FocusStateMachine`, `StateChange`, `FocusState`
- Produces:
```kotlin
data class Segment(val state: FocusState, val startMs: Long, val endMs: Long)
data class BreakPolicy(val maxCount: Int = 3, val maxMinutesEach: Int = 10)
class StudySession(goalMs: Long, startMs: Long, machine: FocusStateMachine, breakPolicy: BreakPolicy = BreakPolicy()) {
  val state: StateFlow<FocusState>
  fun onFrame(s: FrameSignals)
  fun onAppLeft(left: Boolean, timeMs: Long)
  fun startBreak(timeMs: Long): Boolean        // 한도 초과 시 false
  fun endBreak(timeMs: Long)
  fun tick(timeMs: Long)                        // 휴식 초과 검사
  fun focusedMs(nowMs: Long): Long              // STUDYING 구간 합
  fun breaksLeft(): Int
  fun isGoalReached(nowMs: Long): Boolean
  fun finish(nowMs: Long): List<Segment>
}
```
- 소급 처리: `StateChange.startedAtMs`가 현재 열려 있는 구간의 시작보다 뒤면, 열린 구간을 그 시각에서 자르고 새 구간을 연다. 그래서 화면 타이머는 판정 순간에 소급분만큼 줄어든다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `focusedTimeExcludesRetroactiveAway`: 0~60s 공부, 60s부터 얼굴 없음 → 70s에 AWAY 판정 → `focusedMs(70_000) == 60_000`
  - `goalReachedOnlyCountsStudying`: goal 60s인데 중간에 AWAY 30s가 끼면 90s에 `isGoalReached == true`, 89s에는 false
  - `breakLimitThreeTimes`: 네 번째 `startBreak`는 false
  - `breakOverrunBecomesAway`: 휴식 시작 0s → `tick(600_001)` → state AWAY, 해당 구간 시작 = 600_000
  - `appLeftPausesAndResumes`: 앱 이탈 10s 후 복귀 → 그 10s는 APP_LEFT 구간이고 이후는 STUDYING
  - `finishClosesSegmentsContiguously`: 구간들이 빈틈 없이 [start, now]를 덮는다
- [ ] **Step 2:** 실행 → FAIL
- [ ] **Step 3:** 구현
- [ ] **Step 4:** 실행 → PASS
- [ ] **Step 5:** Commit `feat(domain): study session timer`

### Task 4: 리포트, 별 규칙, 로컬 저장

**Files:** Create `domain/Report.kt`, `domain/StarRules.kt`, `data/SessionRecord.kt`, `data/SessionStore.kt` (+ androidMain/iosMain actual); Test `ReportTest.kt`, `StarRulesTest.kt`, `SessionStoreTest.kt`(commonTest, 임시 디렉터리)

**Interfaces:**
- Consumes: `Segment`, `FocusState`
- Produces:
```kotlin
@Serializable data class SessionRecord(val id: String, val startMs: Long, val endMs: Long, val goalMs: Long,
  val segments: List<Segment>, val goalStar: Boolean, val proofStar: Boolean, val proofPhotoPath: String?)   // Segment도 @Serializable로
data class ReportSummary(val totalMs: Long, val focusedMs: Long, val focusRate: Float,
  val byState: Map<FocusState, Pair<Int, Long>>,   // 횟수, 시간(STUDYING 제외)
  val events: List<Segment>)                       // STUDYING 제외, 시간순
fun buildReport(segments: List<Segment>): ReportSummary
data class DayTotal(val date: LocalDate, val focusedMs: Long, val stars: Int)
fun dailyTotals(records: List<SessionRecord>, tz: TimeZone, from: LocalDate, to: LocalDate): List<DayTotal>
fun streakDays(records: List<SessionRecord>, tz: TimeZone, today: LocalDate): Int   // goalStar 있는 연속일
object StarRules { const val DAILY_CAP = 2
  fun goalStarAllowed(goalReached: Boolean, starsToday: Int): Boolean
  fun proofStarAllowed(starsToday: Int): Boolean }
expect fun sessionStoreDir(): String
class SessionStore(dir: String) { fun save(r: SessionRecord); fun all(): List<SessionRecord>; fun starsOn(date: LocalDate, tz: TimeZone): Int }
```
- 날짜는 세션 `startMs` 기준으로 정한다. 저장은 `sessions.json` 파일 하나에 kotlinx-serialization 리스트로 한다. 구현은 `okio` 없이 expect `readText/writeText`로 한다.
  - ponytail: 기록이 수천 개로 늘면 SQLDelight로 바꾼다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `reportComputesRateAndEventCounts`: 공부 50분, AWAY 3분, LOOKING_AWAY 1분 ×2, BREAK 5분 → focusRate = 50/60, byState[LOOKING_AWAY] = (2, 120_000)
  - `goalStarDeniedWhenNotReached`
  - `dailyCapCountsAcrossSessions`: 오늘 이미 별 2개 → 목표·인증 모두 false. 별 1개 → 인증은 true
  - `streakCountsConsecutiveGoalDays`: 오늘·어제·그제 달성, 그끄제 미달성 → 3
  - `storeRoundTrips`: save 두 번 후 `all().size == 2`, 내용 동일
- [ ] **Step 2:** 실행 → FAIL
- [ ] **Step 3:** 구현
- [ ] **Step 4:** 실행 → PASS
- [ ] **Step 5:** Commit `feat(domain): report, stars, local store`

### Task 5: Android 카메라 → FrameSignals (얼굴, 상체)

**Files:** Create `platform/FrameSource.kt`(expect), `androidMain/.../platform/AndroidFrameSource.kt`, `iosMain/.../platform/FrameSource.ios.kt`(TODO 스텁). Manifest에 CAMERA 권한을 추가한다.

**Interfaces:**
- Produces:
```kotlin
interface FrameSource { val signals: Flow<FrameSignals>; val latestFace: StateFlow<FaceCrop?> }   // FaceCrop = 정렬된 112x112 얼굴 RGB (embedding용, 메모리에만)
@Composable expect fun rememberFrameSource(enablePhone: Boolean): FrameSource
@Composable expect fun CameraPreview(modifier: Modifier)
```
- Android 구현:
  - 전면 카메라, CameraX `ImageAnalysis`(STRATEGY_KEEP_ONLY_LATEST)를 쓰고 200ms보다 짧은 간격의 프레임은 버린다(약 5fps).
  - ML Kit FaceDetector는 `CLASSIFICATION_MODE_ALL`, `PERFORMANCE_MODE_FAST`, `minFaceSize 0.15`로 설정한다. yaw = `headEulerAngleY`, pitch = `headEulerAngleX`이다.
  - 얼굴이 없으면 500ms 간격으로 PoseDetector(STREAM_MODE)를 돌린다. 양 어깨 likelihood > 0.5이면 personVisible이다. 코 y가 어깨 평균 y보다 크면 noseBelowShoulders이다.
  - 카메라 권한은 `ActivityResultContracts.RequestPermission`으로 요청한다.
- [ ] **Step 1:** 구현하고 임시 디버그 화면(App.kt)에 미리보기와 신호 텍스트(face/yaw/pitch/eyes/person)를 띄운다.
- [ ] **Step 2:** 기기 확인. 정면을 보면 face=true, 고개를 숙이면 pitch가 음수, 얼굴을 가리고 상체만 보이면 person=true여야 한다. ARTEMIS `mobile_get_device_state`나 스크린샷으로 확인한다.
- [ ] **Step 3:** Commit `feat(android): camera frame signals`

### Task 6: 얼굴 등록 + 본인 확인

**Files:** Create `domain/FaceMath.kt`, `platform/FaceVault.kt`(expect), `androidMain/.../FaceEmbedder.kt`, `androidMain/.../FaceVault.android.kt`, `ui/screens/EnrollScreen.kt`, asset `mobile_face_net.tflite`; Test `FaceMathTest.kt`

**Interfaces:**
- Produces:
```kotlin
fun cosine(a: FloatArray, b: FloatArray): Float
fun averageNormalized(list: List<FloatArray>): FloatArray
const val IDENTITY_THRESHOLD = 0.70f   // 실기기에서 튜닝
interface FaceIdentity { suspend fun embed(face: FaceCrop): FloatArray }
expect object FaceVault { fun save(embedding: FloatArray); fun load(): FloatArray?; fun clear() }
```
- 모델: MobileFaceNet TFLite(입력 112×112, 출력 192-d)를 LiteRT `Interpreter`로 돌린다.
  - 실행 시점에 출처와 라이선스를 확인한다. 쓸 수 없으면 `FaceIdentity`가 항상 null을 주도록 두고(본인 확인 생략), 계획 문서에 기록한다.
- FaceVault Android: `EncryptedSharedPreferences` 대신 Android Keystore AES-GCM 키로 암호화한 바이트를 `SharedPreferences`에 Base64로 저장한다.
- 등록 화면:
  - 안내 문구를 순서대로 보여준다: "정면" → "왼쪽으로 살짝" → "오른쪽으로 살짝" → "위로 살짝" → "아래로 살짝". 각 단계에서 yaw/pitch가 맞으면 자동으로 1장씩 찍어 총 5장을 평균한다.
  - 동의 체크박스("얼굴 정보는 이 기기에만 저장돼요")를 체크해야 시작할 수 있다.
- 본인 재확인: `FrameSource`가 30초마다(그리고 얼굴이 다시 보인 직후) `identityMatch`를 채운다.
- [ ] **Step 1:** `FaceMathTest` 작성: `cosineOfSameVectorIsOne`, `averageIsUnitLength` → FAIL → 구현 → PASS
- [ ] **Step 2:** 임베더, Vault, 등록 화면을 구현한다.
- [ ] **Step 3:** 기기 확인. 본인 얼굴의 유사도와 사진·타인 얼굴의 유사도를 로그로 남겨 임계값이 적절한지 확인하고 값을 기록한다.
- [ ] **Step 4:** Commit `feat: on-device face enrollment & identity`

### Task 7: 세션 준비 흐름 — 본인 확인 → 거치 안내 애니메이션 → 공부 시작 → 기준 자세

**Files:** Create `domain/BaselineCalibrator.kt`, `ui/screens/GoalSetupScreen.kt`, `ui/screens/IdentityCheckScreen.kt`, `ui/screens/PlacementGuideScreen.kt`, `ui/guide/PlacementGuideAnimation.kt`, `ui/screens/BaselineScreen.kt`; Test `BaselineCalibratorTest.kt`

**Interfaces:**
- Produces:
```kotlin
class BaselineCalibrator(durationMs: Long = 3_000) {
  fun add(s: FrameSignals); fun result(): Baseline?   // 3초 이상 쌓였고, 얼굴 프레임이 80% 이상이고, yaw 표준편차 < 8°일 때만 non-null
}
@Composable fun PlacementGuideAnimation(modifier: Modifier)
```
- 화면 순서:
  1. **목표 설정**: 30분 단위 칩 + 직접 입력
  2. **본인 확인**: 기기를 들고 얼굴을 비춘다. 일치하지 않으면 "본인이 아니에요"를 표시하고 넘어가지 않는다.
  3. **거치 안내 페이지**: 애니메이션, 체크 포인트 3개, 작은 미리보기와 "얼굴 인식됨 ✓", **[공부 시작]** 버튼을 둔다. 버튼은 `faceVisible`일 때만 활성화된다.
  4. **기준 자세**: 3초 카운트다운 후 `Baseline`을 저장하고 공부 화면으로 간다. `result() == null`이면 "조금 더 가만히 있어 볼까요?"를 표시하고 다시 시도한다.
- 애니메이션:
  - [docs/image-prompts.md](../../image-prompts.md)의 4장면을 `rememberInfiniteTransition` / `Animatable`로 구현한다.
  - PNG가 아직 없으므로 각 레이어는 `@Composable` 슬롯(`DeskLayer`, `KidLayer`, `TabletLayer`, `FovLayer`, `ThumbsUpLayer`)으로 만든다. 지금은 Canvas 도형으로 그려두고, 나중에 `composeResources/drawable/guide_*.png`가 들어오면 `Image(painterResource(...))`로 바꾼다.
- [ ] **Step 1:** `BaselineCalibratorTest` 작성: `needsThreeSeconds`, `rejectsWhenMostlyNoFace`, `rejectsWhenUnstable`, `averagesYawPitch` → FAIL → 구현 → PASS
- [ ] **Step 2:** 화면과 애니메이션을 구현한다.
- [ ] **Step 3:** 기기 확인. 4개 장면이 반복 재생되는지, 얼굴을 가리면 버튼이 비활성화되는지 본다.
- [ ] **Step 4:** Commit `feat: session setup flow with placement guide`

### Task 8: 공부 화면

**Files:** Create `ui/screens/StudyScreen.kt`, `ui/theme/StateColors.kt`; Modify `ui/App.kt`

**Interfaces:**
- Consumes: `StudySession`, `FrameSource`, `Baseline`
- Produces: `fun stateColor(s: FocusState): Color`, `fun stateLabel(s: FocusState): String`
  - 라벨: 공부 중 / 자리 비움 / 다른 곳 보는 중 / 졸고 있어요 / 폰 사용 중 / 앱 나감 / 휴식 중
- 화면 구성:
  - 큰 순공부 타이머(남은 시간과 진행 링), 상태 배너(배경색이 상태색으로 바뀜), 휴식 버튼("휴식 (n회 남음)"), "공부 끝내기"(확인 다이얼로그), 작은 미리보기를 둔다.
  - `FLAG_KEEP_SCREEN_ON`을 켜고 "충전기를 연결해 주세요" 칩을 보여준다.
- 앱 이탈: 공통 Lifecycle `ON_STOP`/`ON_START`(androidx.lifecycle CMP)에서 `session.onAppLeft`를 호출한다.
- 목표를 달성하면 자동으로 종료하고 `SessionRecord`를 저장한 뒤 보상 화면으로 간다.
- [ ] **Step 1:** 구현한다.
- [ ] **Step 2:** 기기 확인(목표 2분):
  - 자리를 떠나면 약 10초 뒤 회색 '자리 비움'이 뜨고 타이머가 10초 되감기는지 본다.
  - 홈 버튼으로 나갔다가 돌아오면 재개되는지 본다.
  - 휴식 버튼을 누르면 파란색으로 바뀌는지 본다.
- [ ] **Step 3:** Commit `feat: study screen`

### Task 9: 폰 사용 감지

**Files:** Create `androidMain/.../PhoneDetector.kt`, asset `efficientdet_lite0.tflite`; Modify `AndroidFrameSource.kt`

**Interfaces:**
- Produces:
  - `class PhoneDetector(context) { fun detect(bitmap): Boolean; val avgInferenceMs: Long }`
  - `FrameSignals.phoneVisible`에 결과를 연결한다.
- MediaPipe `ObjectDetector`(efficientdet_lite0 int8, 출처 `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite`)를 쓴다.
  - 카테고리 `cell phone`, score ≥ 0.5이고 `personVisible || faceVisible`일 때만 true로 본다.
  - 1초에 1회 돌린다.
- 최근 10회 평균 추론 시간이 150ms를 넘으면 감지를 끈다. 이때 `phoneVisible = false`로 고정하고 로그를 남긴다.
  - ponytail: 손 근접 판정은 생략했다. 오탐이 많으면 MediaPipe HandLandmarker를 추가한다.
- [ ] **Step 1:** 구현한다.
- [ ] **Step 2:** 기기 확인. 폰을 들고 10초가 지나면 '폰 사용 중'이 뜨는지 본다. 탭 A8에서 평균 추론 시간을 기록한다.
- [ ] **Step 3:** Commit `feat(android): phone usage detection`

### Task 10: 보상 화면 + 리포트 화면

**Files:** Create `ui/screens/RewardScreen.kt`, `ui/screens/ReportScreen.kt`, `ui/components/TimelineBar.kt`, `ui/components/TrendChart.kt`

**Interfaces:**
- Consumes: `buildReport`, `dailyTotals`, `streakDays`, `StarRules`, `SessionStore`
- 보상 화면:
  - 목표를 달성했으면 "오늘 목표 달성!"과 별이 튀어나오는 애니메이션, 순공부 시간을 보여준다.
  - 버튼은 **[공부 인증하고 별 더 받기]**, [리포트 보기]이다.
  - 미달성이면 "오늘은 여기까지! 인증하면 별을 받을 수 있어요"를 보여준다.
  - 하루 상한에 걸리면 인증 버튼에 "오늘 별은 다 모았어요"를 표시하고 비활성화한다.
- 리포트 화면:
  - 요약 카드(총시간, 순공부, 집중률 %, 유형별 횟수와 시간)
  - 타임라인 바(구간 비율로 상태색 칠하기)
  - 이벤트 목록("14:32 자리 비움 3분 10초")
  - 주간/월간 탭(일별 막대 + 별 개수 + 연속 달성일)
- [ ] **Step 1:** 구현한다(차트는 Canvas로 직접 그린다. 라이브러리를 추가하지 않는다).
- [ ] **Step 2:** 기기 확인. Task 8의 2분 세션 결과가 리포트와 일치하는지 본다.
- [ ] **Step 3:** Commit `feat: reward & report screens`

### Task 11: 문제집 자동 촬영 인증

**Files:** Create `platform/ProofCamera.kt`(expect), `androidMain/.../ProofCamera.android.kt`, `androidMain/.../QuadDetector.kt`, `domain/AutoCaptureGate.kt`, `ui/screens/ProofDoneScreen.kt`; Test `AutoCaptureGateTest.kt`

**Interfaces:**
- Produces:
```kotlin
class AutoCaptureGate(holdMs: Long = 1_000, minChars: Int = 10) {
  fun onFrame(timeMs: Long, quadFound: Boolean, textChars: Int): Float  // 0..1 진행률, 1 = 촬영
  fun reset()
}
@Composable expect fun ProofCamera(onCaptured: (photoPath: String) -> Unit, onCancel: () -> Unit)
```
- Android 구현:
  - 후면 카메라 ImageAnalysis로 OpenCV(Canny → findContours → approxPolyDP)를 돌려 꼭짓점 4개, 볼록, 화면 면적 20% 이상인 사각형을 찾는다.
  - ML Kit Korean 텍스트 인식은 사각형이 있을 때만 400ms 간격으로 돌린다.
  - 오버레이: 사각형 테두리는 기본 흰색, 조건을 만족하면 초록색으로 바뀌고 원형 진행률이 찬다.
  - 진행률이 1이 되면 `ImageCapture`로 찍고 `warpPerspective`로 보정해 `filesDir/proofs/{sessionId}.jpg`에 저장한다.
- 완료 화면: "공부 인증 완료!", 사진 썸네일, 별 +1을 보여주고 `SessionRecord.proofStar = true`를 저장한다.
- [ ] **Step 1:** `AutoCaptureGateTest` 작성:
  - `capturesAfterOneSecondHold`
  - `resetsWhenQuadLost`
  - `requiresTenChars`: 9자로 2초 → 진행률 0
  - → FAIL → 구현 → PASS
- [ ] **Step 2:** 카메라와 오버레이를 구현한다.
- [ ] **Step 3:** 기기 확인. 문제집은 자동 촬영되고, 빈 A4 종이와 얼굴은 촬영되지 않는지 본다.
- [ ] **Step 4:** Commit `feat: workbook auto-capture proof`

### Task 12: 공유 카드

**Files:** Create `ui/components/ShareCard.kt`, `platform/Share.kt`(expect), `androidMain/.../Share.android.kt`; Modify `studentApp` Manifest(FileProvider, `res/xml/file_paths.xml`)

**Interfaces:**
- Produces: `expect fun shareImage(pngBytes: ByteArray, text: String)`
- 카드: 날짜, 순공부 시간, 집중률, 별 개수, 인증 사진(있으면)과 "MeetStudy" 로고 텍스트를 담는다. `rememberGraphicsLayer()` → `toImageBitmap()` → PNG로 만들고 `ACTION_SEND`로 공유한다.
- 공유 메뉴의 "보호자에게 링크 보내기"와 "선생님에게 보내기"는 비활성화하고 "서버 연결 후 사용 가능"이라고 표시한다(2단계에서 활성화).
- [ ] **Step 1:** 구현한다.
- [ ] **Step 2:** 기기 확인. 공유 시트가 열리고 카드 이미지가 첨부되는지 본다.
- [ ] **Step 3:** Commit `feat: share card`

### Task 13: 전체 흐름 기기 검증

- [ ] **Step 1:** `./gradlew :shared:allTests :studentApp:assembleDebug` 실행 결과 전부 PASS / BUILD SUCCESSFUL
- [ ] **Step 2:** 탭 A8(R8YW70BEYLZ)에서 ARTEMIS로 처음부터 끝까지 확인한다: 첫 실행 → 얼굴 등록 → 목표 2분 → 본인 확인 → 거치 안내 → 공부 시작 → 자리 비움 1회 → 목표 달성 → 별 → 문제집 인증 → 리포트 → 공유
  - 얼굴과 문제집이 필요한 단계는 사람이 직접 해야 하므로 사용자와 함께 진행한다.
- [ ] **Step 3:** 측정값(유사도 분포, 추론 시간, 발열)을 요건정의서 8절에 기록하고 Commit
