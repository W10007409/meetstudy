# MEET TIME 학생앱 (iOS)

Compose Multiplatform 화면(`shared`)을 그대로 띄우는 iOS 앱입니다. iOS 15 이상, Apple Silicon Mac에서 빌드합니다.

## 빌드

```sh
brew install xcodegen
cd iosApp && xcodegen          # iosApp.xcodeproj 생성 (커밋하지 않음)
open iosApp.xcodeproj
```

Xcode에서 Signing & Capabilities의 Team을 고른 뒤 실기기로 실행합니다. 빌드할 때 Xcode가 `./gradlew :shared:embedAndSignAppleFrameworkForXcode`를 실행하므로 JDK 17이 필요합니다. Xcode가 Java를 못 찾으면 터미널에서 `JAVA_HOME`을 설정한 뒤 그 터미널에서 `open iosApp.xcodeproj`로 여세요.

## Android와 다른 점

| 기능 | iOS 상태 |
|---|---|
| 얼굴·고개 각도·눈 감김 | Vision 얼굴 랜드마크 |
| 얼굴이 안 보일 때 상체 감지 | Vision 신체 자세 |
| 문제집 인증 사진 | VisionKit 문서 스캐너 (자동 촬영, 사각형 보정) |
| 본인 확인 | 꺼짐. MobileFaceNet을 iOS에서 돌릴 TFLite/CoreML 연동이 아직 없음 |
| 폰 사용 감지 | 꺼짐. 같은 이유 |

실기기에서 확인할 것: 인식 화면을 켜고 고개를 숙였을 때 pitch가 음수가 되는지 봅니다. 양수가 되면 `FrameSource.ios.kt`의 `VISION_PITCH_SIGN`을 `1f`로 바꿉니다.

시뮬레이터에는 전면 카메라가 없어서 감지 신호가 나오지 않습니다. 화면 흐름만 볼 수 있습니다.
