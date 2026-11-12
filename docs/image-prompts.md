# MeetStudy 이미지 생성 프롬프트

카메라 거치 안내 애니메이션에 쓸 일러스트입니다. 앱 안에서 **레이어를 따로 움직이는** 방식이므로, 모든 이미지를 같은 스타일과 시점으로 만들고 **배경 투명 PNG**로 받습니다.

## 공통 스타일 (모든 프롬프트 앞에 붙이세요)

```
Flat 2D vector illustration for a kids' study app, bright and playful, soft pastel palette
(mint green #7ED6B4, sky blue #8EC5FF, butter yellow #FFD966, coral #FF9B85), thick rounded
outlines in dark navy #2E3A59, simple shapes, no gradients, no text, consistent 3/4 top-down
isometric-ish view, transparent background, centered, high resolution 2048x2048.
```

## 레이어 목록

| # | 파일명 | 프롬프트 (공통 스타일 뒤에 이어 붙임) |
|---|---|---|
| 1 | `guide_desk.png` | `A tidy wooden study desk seen from a 3/4 top-down angle, an open workbook and a pencil on it, empty front-left corner of the desk clearly visible, no people.` |
| 2 | `guide_kid.png` | `A cheerful gender-neutral school kid (around 10-15 years old) sitting at a desk, upper body only, looking down at a book with a slight smile, short hair, mint hoodie. Same 3/4 view as a desk illustration. Character only.` |
| 3 | `guide_kid_carry.png` | `The same cheerful gender-neutral kid in a mint hoodie, standing and walking while holding a tablet with both hands, happy expression, full body. Character only.` |
| 4 | `guide_tablet_stand.png` | `A tablet on a small foldable stand, rear camera side facing the viewer slightly, screen tilted, cute rounded design, sky blue case. Object only.` |
| 5 | `guide_camera_fov.png` | `A semi-transparent butter-yellow cone / fan shape representing a camera's field of view, with small sparkles at the wide end. Shape only, 60% opacity look.` |
| 6 | `guide_thumbs_up.png` | `A big friendly cartoon hand giving a thumbs up, with three small sparkle stars around it. Object only.` |
| 7 | `guide_check_icons.png` | `Three round sticker icons in a row: a smiling face next to a book, a ruler showing arm length, and a sun with a charging plug. Icons only, equal spacing.` |

## 애니메이션 조합 (구현 참고)

1. `guide_kid_carry`가 왼쪽에서 걸어 들어옴 (translateX, 0.8초)
2. `guide_tablet_stand`가 책상 앞 모서리에 툭 놓임 (scale 0.6→1.0 바운스) → 45° 회전 (rotation, 0.5초)
3. `guide_kid`로 교체되고 `guide_camera_fov`가 부채꼴로 펼쳐짐 (scaleX 0→1, 0.6초), 반짝임
4. `guide_thumbs_up` 팝업 (scale 바운스) → 1초 대기 후 반복

> 생성 도구에서 캐릭터 일관성이 깨지면 #2와 #3은 같은 시드나 레퍼런스 이미지를 쓰세요.
