# 0.2.0 업데이트 검증 — 2026-11-10

상점, 친구 요청, 장비 교환을 추가한 버전이다. 기존 육성 데이터에 교환 대기 기록을 추가하며, Windows 시연 실행기는 함께 제공하는 친구 서버를 PC에서 실행한다.

## 결과

**총 55개 테스트 통과.** Android 검증은 공식 Pleos Connect Emulator 2.1.0 / API 34 / x86_64에서 수행했다. 게임 흐름과 친구 교환에는 명시적인 fixture 앱을 사용했다.

| 구분 | 통과 | 확인한 내용 | 기록 |
|---|---:|---|---|
| 전투·보상·상점 규칙 | 32 | 기존 전투 20개, 구매 금액·레벨·중복·판매·장착 제한·전송 취소·기존 저장 형식 12개 | [단위 테스트](unit-tests.json) |
| Android 저장 | 6 | 재시작 후 진행 유지, 중복 처리, 전송 대기와 가방 차감의 동시 저장, 수령 영수증 유지 | [저장 테스트](instrumentation-storage.txt) |
| 원정 화면 | 1 | 가상 운행, 보스 보상, 상자·장비·도감 화면 | [원정 화면 테스트](instrumentation-ui.txt) |
| 상점 화면 | 1 | 확인 창을 거쳐 45코인 차감·강화 가루 5개 지급, 친구 화면 진입 | [상점 화면 테스트](instrumentation-commerce.txt) |
| Android ↔ 서버 교환 | 1 | 두 사용자 친구 승인, 장비 맡기기, 제안·수락, 가방 수령과 재시도 | [교환 통합 테스트](instrumentation-social.txt) |
| 서버 | 14 | 승인·권한, 중복 요청, 취소 후 늦은 전송, 소유권 동시 변경, 경쟁 제안·취소·만료, 재시작 복구 | [서버 테스트 소스](../../server/tests/test_store.py) |

통합 교환은 서로 다른 기기 토큰을 가진 두 사용자로 실행했다. Android 앱의 친구 기능과 HTTP 클라이언트가 같은 실제 로컬 서버에 연결했다. 희귀 +3 장비와 영웅 +2 장비를 교환한 뒤 소유권과 강화 수치를 확인했으며, 같은 수령을 다시 요청해도 장비·가루가 추가 지급되지 않았다.

## 설치와 데이터 유지

- 버전 이름 `0.2.0`, 버전 코드 `2`. 서명된 release APK의 `debuggable=false`를 확인했다.
- release APK는 이전 배포와 같은 서명키를 사용한다. 서명키는 공개 파일에 포함하지 않는다.
- 기존 fixture 앱 위에 새 버전을 설치했고, 게임 진행을 유지한 상태로 실행했다.
- Room DB는 버전 1에서 2로 이전한다. 기존 DB 파일을 유지하고 `economy` 테이블을 추가한다.
- 시연 실행기를 다시 실행해 로컬 친구 서버 확인, 앱 설치 및 게임 화면 열기까지 확인했다.
- [release 파일 정보](../../apk/release-manifest.json)와 [debug 파일 정보](../../artifacts/build-manifest.json)에 크기와 SHA-256을 기록했다.

## 재현 방법

저장소를 D드라이브에 풀고 `setup.cmd`로 환경을 준비한다. 이후 아래 명령을 저장소 폴더에서 실행한다.

```powershell
$env:TEMP = 'D:\Codex\.tmp'
$env:TMP = $env:TEMP
$env:TMPDIR = $env:TEMP

# 전투·보상·상점 규칙, debug 앱과 Android 테스트 APK
& .\tools\build.ps1 -Tasks @(':core-game:test', ':app-boss:assembleFixtureDebug', ':app-boss:assemblePleosDebug', ':app-boss:assembleFixtureDebugAndroidTest')

# 친구 서버와 시연 앱
.\social-server.cmd
.\demo.cmd

# 서버 규칙 검증
python -m unittest discover -s server/tests -v

# Android 저장·화면·두 사용자 교환 검증
python .\tools\verify_android.py --fixture-tests
```

교환을 인터넷에서 사용하려면 같은 서버를 HTTPS로 배포하고 두 앱에서 그 주소를 사용한다. 로컬 서버의 시작·저장 위치·연결 방법은 [서버 안내](../../server/README.md)를 따른다. 차량 API 연결 기록은 [SDK 검증 기록](SDK_VALIDATION.md)에 별도로 정리했다.
