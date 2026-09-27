# 바꿔 (Bakkwo)

클립보드에 복사한 문장을 홈 화면 위젯의 버튼 한 번으로 바로 번역해주는 안드로이드 앱입니다.
번역은 Google Gemini API(무료 티어 제공)로 처리하며, 한국어 → 영어 / 그 외 언어 → 한국어를
자동으로 판단하기 때문에 매번 "영어로 번역해줘" 같은 프롬프트를 입력할 필요가 없습니다.

## 사용 흐름

1. 어디서든 번역하고 싶은 문장을 **복사(길게 눌러 복사)**합니다.
2. 홈 화면의 **바꿔 위젯**에서 **번역** 버튼을 한 번 탭합니다.
3. 위젯 안에 번역 결과가 바로 표시됩니다. **복사** 버튼을 누르면 결과가 클립보드에 담깁니다.

다른 앱(카톡, 브라우저 등)에서 텍스트를 선택 → **공유** → **바꿔**를 선택해도 같은 방식으로
(작은 팝업으로) 즉시 번역됩니다. 앱을 직접 열지 않아도 되는 두 가지 경로를 모두 제공합니다.

앱을 직접 열면 일반 AI 챗처럼 대화가 이어지는 채팅 화면이 나옵니다 — 첫 화면엔 입력창과 대화
내용만 보이고, API 키 설정·위젯 사용법·프롬프트 수정 같은 부가 기능은 오른쪽 위 메뉴(⋮) 뒤에
숨겨 두었습니다. 대화 기록은 기기에 저장되어 앱을 껐다 켜도 이어집니다.

## 왜 위젯 안에 입력창이 없나요?

안드로이드 홈 화면 위젯(AppWidget/RemoteViews)은 시스템 제약상 **EditText(텍스트 입력창)를
지원하지 않습니다.** 계산기나 유튜브 뮤직 위젯도 실제로는 위젯에 미리 정의된 **버튼**만 있고,
탭하면 앱 프로세스가 그 결과(숫자, 재생 상태 등)를 다시 위젯에 그려주는 방식입니다. 바꿔도 같은
패턴을 사용합니다: 위젯의 "번역" 버튼 → 클립보드 읽기 → Gemini 호출 → 위젯 갱신.

## 프로젝트 구조

- `app/src/main/java/com/bakkwo/translate/`
  - `ui/MainActivity.kt` — API 키 설정 + 앱 안에서 직접 번역해보는 화면(입력 중 자동 번역) + 위젯 추가 버튼
  - `ui/ShareTranslateActivity.kt` — 다른 앱에서 "공유"로 들어온 텍스트를 즉시 번역해 보여주는 작은 팝업
  - `ui/ClipboardTranslateActivity.kt` — 위젯의 "번역" 버튼이 여는 투명 액티비티. 클립보드는
    포커스를 가진 액티비티에서만 읽을 수 있어서, 클립보드를 읽자마자 `WorkManager`에 실제 번역
    작업을 넘기고 화면을 그리지 않은 채 바로 닫힙니다.
  - `widget/BakkwoWidgetProvider.kt` — 위젯 레이아웃 갱신, 버튼 PendingIntent 연결
  - `widget/WidgetActionReceiver.kt` — 위젯의 "복사" 버튼 처리
  - `worker/TranslateWorker.kt` — 실제 Gemini API 호출을 수행하는 백그라운드 작업(액티비티
    생명주기와 무관하게 끝까지 실행됨)
  - `data/GeminiClient.kt` — Gemini `generateContent` API 직접 호출(HttpURLConnection, 의존성 최소화)
  - `data/Prefs.kt` — API 키 및 마지막 번역 결과를 앱 전용 저장소(SharedPreferences)에 보관

## 빌드 방법

이 환경에는 Android SDK가 설치되어 있지 않아 APK를 직접 빌드/실행해보지는 못했지만, GitHub
Actions 워크플로우(`.github/workflows/android-build.yml`)가 push할 때마다 자동으로 디버그 APK를
빌드합니다. 결과물은 두 군데서 받을 수 있어요:

- **Releases 탭 → `debug-latest`** — push할 때마다 릴리스 자체를 통째로 새로 만들고, APK
  파일명에도 커밋 해시를 붙여서(`bakkwo-translator-debug-<sha>.apk`) 매번 새로운 다운로드
  URL로 받게 됩니다. (같은 파일명을 계속 재사용하면 GitHub CDN이 예전 파일을 한동안 캐싱해
  서빙하는 경우가 있어서, 방금 올린 최신 빌드인데도 서명이 다른 예전 APK가 받아지고 그걸
  설치하려다 "App not installed"가 나는 문제가 있었습니다.) 압축 없이 `.apk`를 그대로 받습니다.
- **Actions 탭 → 해당 실행 → Artifacts** — GitHub Actions 아티팩트 특성상 항상 `.zip`으로
  감싸져 있어 압축을 풀어야 합니다.

> **기존에 이미 설치해보신 분들만**: 초기 몇 번의 빌드는 CI 러너가 매번 임시 디버그 키로 서명해서,
> 이후 `keystore/debug.keystore`를 고정하기 전까지는 새 APK를 덮어설치할 수 없었습니다
> (서명이 다르면 안드로이드가 업데이트 설치를 거부합니다). **한 번만** 기존 앱을 삭제하고 최신
> APK를 새로 설치해주세요. 그 다음부터는 항상 같은 키로 서명되어 삭제 없이 바로 업데이트됩니다.

직접 빌드하려면:

1. Android Studio → `Open` → 이 저장소 폴더 선택
2. Gradle sync가 끝날 때까지 대기 (최초 1회는 Android SDK/AGP 관련 컴포넌트를 자동 다운로드)
3. 갤럭시 기기를 USB 디버깅으로 연결하거나 에뮬레이터 실행 후 `Run ▶` (minSdk 26 = Android 8.0 이상)
4. 앱 실행 후 상단 "설정"에 [Google AI Studio](https://aistudio.google.com/apikey)에서 무료로
   발급받은 Gemini API 키(`AIza...`)를 붙여넣고 저장
5. "위젯" 섹션의 **홈 화면에 위젯 추가** 버튼을 누르거나, 홈 화면을 길게 눌러 위젯 목록에서
   **바꿔**를 직접 추가

## API 키 보안

- API 키는 앱 전용 `SharedPreferences`(다른 앱이 접근 불가)에만 저장되며, 기기 밖으로는 오직
  Gemini API 호출(HTTPS)에만 사용됩니다. 별도 백엔드 서버는 없습니다 — 기기에서 Google API로
  직접 요청합니다.
- 더 강한 보호가 필요하면 `data/Prefs.kt`의 `SharedPreferences`를
  `androidx.security:security-crypto`의 `EncryptedSharedPreferences`로 교체할 수 있습니다.

## 커스터마이즈

- **숨은 설정**: 앱 메인 화면에서 상단의 "바꿔" 제목을 **길게 누르면** 번역 프롬프트를 직접
  수정하는 대화상자가 뜹니다. 여기서 바꾼 프롬프트는 기기에 저장되어 이후 모든 번역(위젯 포함)에
  적용되며, "기본값으로 초기화" 버튼으로 원래 프롬프트로 되돌릴 수 있습니다.
- 기본 번역 방향(한국어↔영어 자동 감지)은 `data/GeminiClient.kt`의 `DEFAULT_SYSTEM_PROMPT`에서 수정
- 모델은 같은 파일의 `MODEL` 상수(`gemini-3.8-flash`)에서 변경

## Gemini 무료 티어 참고

- Google AI Studio에서 발급한 키는 신용카드 등록 없이 무료 티어로 바로 사용할 수 있습니다.
- 다만 무료 티어는 분당/일별 요청 수 제한이 있고, 무료로 보낸 데이터는 Google이 모델 개선에
  활용할 수 있다는 정책이 있으니 민감한 문장을 번역할 계획이라면 Google AI Studio의 최신 약관을
  확인해보세요.
