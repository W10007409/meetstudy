# MEET TIME 이미지 생성 프롬프트 — 카메라 거치 안내

거치 안내 화면은 기기를 **어디에, 어떤 각도로, 얼마나 떨어뜨려** 두는지 한눈에 알려주는 것이 목적입니다. 그래서 그림은 귀여운 캐릭터보다 **제품 설치 설명서**처럼 정확하고 깔끔하게 만듭니다. 재미는 앱에서 움직임과 반짝임 효과로 더합니다.

## 만들기 전에 꼭 지킬 것
- **전면 카메라:** 앱은 전면 카메라를 씁니다. 태블릿 **화면이 학생 쪽을 향해야** 합니다. 예전 프롬프트의 "후면 카메라가 보는 사람 쪽" 설정은 틀렸습니다.
- **글자:** 그림에 글자를 넣지 않습니다. 안내 문구, 각도 숫자, ✓/✕ 표시는 앱에서 겹쳐 그립니다.
- **같은 캔버스:** 같은 장면에 들어가는 레이어는 **캔버스 크기와 구도를 똑같이** 맞춥니다. 각 물체는 최종 위치에 그리고 나머지는 투명하게 둡니다. 그래야 앱에서 그대로 겹쳐 쓸 수 있습니다.
- **만드는 순서:** 먼저 **완성 장면(#A0, #B0)을 한 장으로** 만듭니다. 그다음 같은 시드나 레퍼런스 이미지, 인페인팅으로 레이어를 하나씩 분리합니다. 따로따로 생성하면 원근과 크기가 어긋납니다.
- **파일 형식:** 배경이 투명한 PNG입니다.

## 공통 스타일 (모든 프롬프트 앞에 붙이세요)

```
Clean instructional illustration in the style of a modern product setup guide.
Semi-flat vector shading with subtle soft shadows, realistic human proportions (not chibi, not cartoonish),
calm muted palette: warm light wood #D8B98A, off-white #F4F1EA, slate blue #5B7DB1, teal accent #2FA39A,
charcoal outlines #2B2F36 at medium weight. Simple background-free composition, no text, no letters,
no numbers, no logos, transparent background, high resolution.
```

## 장면 A — 위에서 내려다본 배치도 (각도 설명용)

캔버스는 **2048×2048**이고, 정확히 위에서 내려다본 구도(top-down plan view)입니다. 학생은 화면 아래쪽에 앉고, 책상은 화면 위쪽으로 놓입니다.

| # | 파일명 | 프롬프트 (공통 스타일 뒤에 이어 붙임) |
|---|---|---|
| A0 | `guide_top_full.png` | `Exact top-down plan view of a study desk. A student seen from directly above sits at the bottom edge of the desk (head and shoulders visible, hands on an open workbook in the center of the desk). A tablet on a small stand sits at the far front-right corner of the desk, its screen facing the student, rotated about 30 degrees toward the student. Reference composite for layering.` |
| A1 | `guide_top_desk.png` | `Same top-down plan view: only the desk with an open workbook and a pencil, no student, no tablet. Desk occupies the same position as in the reference.` |
| A2 | `guide_top_student.png` | `Same top-down plan view: only the student seen from above (head, shoulders, arms resting forward), in the same position as in the reference. Nothing else.` |
| A3 | `guide_top_tablet.png` | `Same top-down plan view: only the tablet on its stand, seen from above, drawn facing straight down toward the student (0 degrees, not rotated), centered on its own rotation pivot. Nothing else.` |

> A3은 회전하지 않은 상태로 받습니다. 앱이 0°에서 45°까지 돌리며, 허용 범위를 나타내는 초록 부채꼴 띠와 각도 숫자는 앱이 그립니다.

## 장면 B — 학생 옆 비스듬한 위쪽 시점 (완성 모습 설명용)

캔버스는 **2048×1536**입니다. 학생의 왼쪽 앞, 비스듬한 위쪽에서 본 3/4 시점이라 학생 얼굴과 태블릿 화면이 모두 보입니다.

| # | 파일명 | 프롬프트 (공통 스타일 뒤에 이어 붙임) |
|---|---|---|
| B0 | `guide_side_full.png` | `Elevated three-quarter view from the front-left of a study desk. A middle-school student with realistic proportions sits upright, writing in an open workbook. A tablet on a small stand stands at the front corner of the desk about one arm's length away, screen and front camera facing the student's face at a slight angle. Even, bright room light from the side. Reference composite for layering.` |
| B1 | `guide_side_desk.png` | `Same elevated three-quarter view: only the desk, chair and open workbook, no person, no tablet, same position as the reference.` |
| B2 | `guide_side_student.png` | `Same elevated three-quarter view: only the seated student writing in a workbook (upper body and chair-seated legs), same pose and position as the reference. Nothing else.` |
| B3 | `guide_side_tablet.png` | `Same elevated three-quarter view: only the tablet on its stand at the front corner of the desk, screen facing the student, same position as the reference. Nothing else.` |
| B4 | `guide_side_hand_place.png` | `Same elevated three-quarter view: only a student's forearm and hand setting the tablet-on-stand down onto the front corner of the desk, captured just before it touches the desk. Same tablet size and position as the reference.` |
| B5 | `guide_side_arm_reach.png` | `Same elevated three-quarter view: only the seated student extending one arm straight forward so the fingertips just reach the tablet, showing the arm-length distance. Same position as the reference.` |

> 카메라 시야를 나타내는 반투명 부채꼴, 얼굴과 책을 감싸는 초록 테두리, 반짝임은 **앱에서 그립니다**. 그림으로 받으면 원근을 맞추기 어렵기 때문입니다.

## 장면 C — 이렇게 하면 안 돼요 (비교 카드)

캔버스는 **1024×1024**이고, 장면 B와 같은 스타일과 같은 학생입니다. 각 카드는 잘못된 점 **한 가지만** 분명하게 보여줍니다. 빨간 ✕는 앱이 붙입니다.

| # | 파일명 | 프롬프트 (공통 스타일 뒤에 이어 붙임) |
|---|---|---|
| C1 | `guide_bad_too_close.png` | `The same student and tablet: the tablet is placed very close to the student's face, almost touching the workbook edge, so only part of the face would be in view.` |
| C2 | `guide_bad_flat.png` | `The same student and tablet: the tablet lies flat on the desk next to the workbook, screen facing the ceiling, so its camera cannot see the student's face.` |
| C3 | `guide_bad_side.png` | `The same student and tablet: the tablet stands far to the side of the desk, facing the student's profile at a steep side angle of about 80 degrees.` |
| C4 | `guide_bad_backlight.png` | `The same student and tablet: a bright window directly behind the student, the student's face falling into shadow against the glare.` |

## 체크 아이콘

| # | 파일명 | 프롬프트 (공통 스타일 뒤에 이어 붙임) |
|---|---|---|
| D1 | `guide_icons.png` | `Three simple line icons in a row with equal spacing, same stroke weight, teal #2FA39A on transparent background: (1) a face outline above an open book, (2) an arm with a horizontal double-headed measuring arrow, (3) a lamp next to a power plug. Icon set style, no fills, no text.` |

> 체크 문구 "얼굴과 책이 같이 보이게", "팔 길이만큼 떨어뜨려서", "밝은 곳에서, 충전기 연결"과 순서를 맞췄습니다.

## 애니메이션 구성 (한 바퀴 약 9초, 반복)

1. **어디에 두나요 (장면 A, 약 3초)**
   - A1과 A2가 깔린 상태에서, A3이 책상 앞 모서리로 미끄러져 들어옵니다(0.6초).
   - A3이 0°에서 45°까지 천천히 돌아갑니다(1초). 동시에 앱이 그린 초록 띠(0~45°)가 펼쳐지고, 각도 숫자가 따라 움직입니다.
   - 문구: "책상 앞 모서리에, 정면부터 45도까지"
2. **놓아요 (장면 B, 약 2초)**
   - B1과 B2 위로 B4의 손이 태블릿을 내려놓습니다(살짝 바운스).
   - 그다음 B4를 B3으로 바꿉니다.
   - 문구: "팔 길이만큼 떨어뜨려요". B5를 0.8초 동안 겹쳐 보여줍니다.
3. **잘 보이나요 (장면 B, 약 2초)**
   - 앱이 B3에서 학생 쪽으로 반투명 시야 부채꼴을 펼칩니다(0.6초).
   - 얼굴과 책 위에 초록 테두리가 차례로 생기며 반짝입니다.
   - 문구: "얼굴과 책이 함께 보이면 성공!"
4. **이건 안 돼요 (장면 C, 약 2초)**
   - C1~C4가 0.5초씩 넘어가고, 카드마다 빨간 ✕가 붙습니다.
   - 처음 보는 사용자가 아니면 이 단계를 건너뛰어도 됩니다.

> 고르기 쉽도록: 장면 A와 B만 있어도 핵심 안내가 됩니다. 장면 C는 시간이 있을 때 추가하세요. 먼저 A0과 B0 두 장만 뽑아서 분위기를 확인한 뒤 레이어를 분리하는 것을 권합니다.
