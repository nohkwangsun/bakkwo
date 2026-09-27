# 바꾸 (Bakkwo)

클립보드에 복사한 문장을 홈 화면 위젯의 버튼 한 번으로 바로 번역해주는 안드로이드 앱입니다.
번역은 Anthropic Claude API(Claude Haiku 4.5)로 처리하며, 한국어 → 영어 / 그 외 언어 → 한국어를
자동으로 판단하기 때문에 매번 "영어로 번역해줘" 같은 프롬프트를 입력할 필요가 없습니다.

## 사용 흐름

1. 어디서든 번역하고 싶은 문장을 **복사(길게 눌러 복사)**합니다.
2. 홈 화면의 **바꾸 위젯**에서 **번역** 버튼을 한 번 탭합니다.
3. 위젯 안에 번역 결과가 바로 표시됩니다. **복사** 버튼을 누르면 결과가 클립보드에 담깁니다.

다른 앱(카톡, 브라우저 등)에서 텍스트를 선택 → **공유** → **바꾸**를 선택해도 같은 방식으로
(작은 팝업으로) 즉시 번역됩니다. 앱을 직접 열지 않아도 되는 두 가지 경로를 모두 제공합니다.

## 왜 위젯 안에 입력창이 없나요?

안드로이드 홈 화면 위젯(AppWidget/RemoteViews)은 시스템 제약상 **EditText(텍스트 입력창)를
지원하지 않습니다.** 계산기나 유튜브 뮤직 위젯도 실제로는 위젯에 미리 정의된 **버튼**만 있고,
탭하면 앱 프로세스가 그 결과(숫자, 재생 상태 등)를 다시 위젯에 그려주는 방식입니다. 바꾸도 같은
패턴을 사용합니다: 위젯의 "번역" 버튼 → 클립보드 읽기 → Claude 호출 → 위젯 갱신.

## 프로젝트 구조

- `app/src/main/java/com/bakkwo/translate/`
  - `ui/MainActivity.kt` — API 키 설정 + 앱 안에서 직접 번역해보는 화면(입력 중 자동 번역) + 위젯 추가 버튼
  - `ui/ShareTranslateActivity.kt` — 다른 앱에서 "공유"로 들어온 텍스트를 즉시 번역해 보여주는 작은 팝업
  - `ui/ClipboardTranslateActivity.kt` — 위젯의 "번역" 버튼이 여는 투명 액티비티. 클립보드는
    포커스를 가진 액티비티에서만 읽을 수 있어서, 클립보드를 읽자마자 `WorkManager`에 실제 번역
    작업을 넘기고 화면을 그리지 않은 채 바로 닫힙니다.
  - `widget/BakkwoWidgetProvider.kt` — 위젯 레이아웃 갱신, 버튼 PendingIntent 연결
  - `widget/WidgetActionReceiver.kt` — 위젯의 "복사" 버튼 처리
  - `worker/TranslateWorker.kt` — 실제 Anthropic API 호출을 수행하는 백그라운드 작업(액티비티
    생명주기와 무관하게 끝까지 실행됨)
  - `data/AnthropicClient.kt` — Anthropic Messages API 직접 호출(HttpURLConnection, 의존성 최소화)
  - `data/Prefs.kt` — API 키 및 마지막 번역 결과를 앱 전용 저장소(SharedPreferences)에 보관

## 빌드 방법

이 환경에는 Android SDK가 설치되어 있지 않아 APK를 직접 빌드/실행해보지는 못했습니다.
**Android Studio(Koala 이상 권장)** 로 이 폴더를 열면 Gradle sync 후 바로 빌드할 수 있도록
`gradlew`/`build.gradle.kts` 등은 준비해 두었습니다.

1. Android Studio → `Open` → 이 저장소 폴더 선택
2. Gradle sync가 끝날 때까지 대기 (최초 1회는 Android SDK/AGP 관련 컴포넌트를 자동 다운로드)
3. 갤럭시 기기를 USB 디버깅으로 연결하거나 에뮬레이터 실행 후 `Run ▶` (minSdk 26 = Android 8.0 이상)
4. 앱 실행 후 상단 "설정"에 [Anthropic Console](https://console.anthropic.com/settings/keys)에서
   발급받은 API 키(`sk-ant-...`)를 붙여넣고 저장
5. "위젯" 섹션의 **홈 화면에 위젯 추가** 버튼을 누르거나, 홈 화면을 길게 눌러 위젯 목록에서
   **바꾸**를 직접 추가

## API 키 보안

- API 키는 앱 전용 `SharedPreferences`(다른 앱이 접근 불가)에만 저장되며, 기기 밖으로는 오직
  Anthropic API 호출(HTTPS)에만 사용됩니다. 별도 백엔드 서버는 없습니다 — 기기에서 Anthropic API로
  직접 요청합니다.
- 더 강한 보호가 필요하면 `data/Prefs.kt`의 `SharedPreferences`를
  `androidx.security:security-crypto`의 `EncryptedSharedPreferences`로 교체할 수 있습니다.

## 커스터마이즈

- 번역 방향(현재: 한국어↔영어 자동 감지)은 `data/AnthropicClient.kt`의 `SYSTEM_PROMPT`에서 수정
- 모델은 같은 파일의 `MODEL` 상수(`claude-haiku-4-5-20251001`)에서 변경
