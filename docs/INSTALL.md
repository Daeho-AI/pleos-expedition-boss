# 설치와 문제 해결

Windows 자동 설치는 D드라이브를 전제로 한다. 다른 운영체제에서는 `android/`를 Android Studio로 열고 공식 Pleos 개발 환경을 구성한다. macOS/Linux에서 이 저장소의 Windows 실행기를 검증하지 않았다.

## 첫 설치

1. [Windows 시연 ZIP](https://github.com/Daeho-AI/pleos-expedition-boss/releases/download/v0.1.0-preview.1/ExpeditionBoss-Windows-Demo.zip)을 `D:\Codex\ExpeditionBoss`에 압축 해제한다.
2. Python 3.11 이상을 설치한다. `py -3 --version` 또는 `python --version`으로 확인한다.
3. `setup.cmd`를 실행한다. 최초 다운로드는 네트워크에 따라 오래 걸릴 수 있다. 완료 후 게임 창이 열린다.
4. 다음에는 `demo.cmd`로 실행한다.

ZIP에는 Google·현대의 SDK나 에뮬레이터 이미지를 재배포하지 않는다. 설치기가 공식 배포처에서 받으며 각 구성요소의 이용 조건이 적용된다. 네트워크나 배포처 접근 제한이 있으면 중단한다.

## 문제 해결

| 증상 | 확인할 내용 |
|---|---|
| Python을 찾지 못함 | Python 3.11 이상 설치 후 새 창에서 다시 실행. `py -3`가 선택하는 버전도 확인 |
| D드라이브 오류 | D의 폴더에 모두 압축 해제했는지 확인. 이 도구는 C로 자동 전환하지 않음 |
| 실행 정책 오류 | 직접 `.ps1` 대신 `setup.cmd`/`demo.cmd` 사용. 조직 PC 정책이 실행을 차단하면 관리자에게 확인 |
| 가상 기기가 시작 직후 종료 | CPU 가상화 및 Windows Hypervisor Platform 설정 확인. [Android 공식 가속 안내](https://developer.android.com/studio/run/emulator-acceleration) 참고 |
| 기기가 계속 준비 중 | `D:\Codex\PleosBossEmulator\emulator-error.log`와 저장소 `work/launcher-last.log` 확인. 기본 대기 제한 240초 |
| 다른 기기가 연결됐다고 나옴 | 실행기는 `emulator-5554`의 공식 Pleos 이미지를 확인함. 다른 AVD가 포트 5554를 사용 중이면 해당 기기를 직접 종료하거나 다른 포트로 변경 |
| 이미 실행 중이라는 안내 | 중복 실행을 막는 정상 메시지. 처음 열린 창을 기다림 |
| APK 서명 불일치 | SDK debug와 release는 같은 ID지만 다른 서명. 새 기기를 사용. 실행기는 기존 앱/데이터를 자동 삭제하지 않음 |
| APK가 없다고 나옴 | `setup.cmd` 실행. SDK debug가 필요하면 아래 다운로드 명령 사용 |
| 실제 SDK 버전에서 보스 체력이 그대로 | 유효한 내비 안내·차량 이동·양수 제한속도가 모두 필요. 테스트 환경에서 양수 제한속도 미확인은 알려진 제한사항 |

## 선택 실행

PowerShell에서 저장소 폴더를 연 뒤 실행한다.

```powershell
# 실행하지 않고 설치 준비만 확인
powershell -ExecutionPolicy Bypass -File .\tools\setup.ps1 -PrepareOnly

# SDK 진단용 debug APK를 다운로드하고 실행
powershell -ExecutionPolicy Bypass -File .\tools\download_apks.ps1 -Mode pleos
powershell -ExecutionPolicy Bypass -File .\tools\run_demo.ps1 -Mode pleos
```

SDK 모드 실행기는 Virtual Console용 8088 포트를 연결한다. 브라우저에서 `http://127.0.0.1:8088/`을 열어 공식 콘솔 세션을 활성화한다. 가상 차량 값 변경은 에뮬레이터 시험에서만 사용한다.

게임 데이터를 지우는 명령은 기본 설치·실행에 없다. 개발 검증의 `--reset-demo` 옵션만 시연 앱의 진행 데이터를 초기화한다.

## release 빌드와 서명키

`tools/prepare_release_signing.py`는 최초 한 번 `D:\Codex\PleosBossToolchain\signing`에 키와 설정을 생성한다. 기존 키가 있으면 그대로 재사용한다. 후속 APK 업데이트에는 같은 키가 필요하므로 이 폴더를 별도 비공개 위치에 백업한다. 저장소나 Releases에 업로드하지 않는다.

일반 Gradle에서 서명하려면 `BOSS_RELEASE_SIGNING_FILE` 환경변수로 비공개 properties 파일 경로를 지정한다. 필요한 항목은 `storeFile`, `storePassword`, `keyAlias`, `keyPassword`다. 지정하지 않으면 Gradle의 release 결과물은 미서명 APK이므로 배포하지 않는다.
