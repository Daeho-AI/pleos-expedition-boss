# 0.3.0 복귀 정산 · 필살의 일격 검증

검증일: 2026-11-13. 대상은 `versionName=0.3.0`, `versionCode=3`이다.

## 실행 규칙

| 상태 | 동작 |
|---|---|
| P 기어 + UX 제한 해제 + 앱 전면 | 초당 자동 전투, 보스 처치 시 상자와 경험치 저장 |
| 주행, 주차 확인 불가, 앱을 떠남 | 전투 루프·게임 화면·연출 중단. 백그라운드 전투 서비스 없음 |
| 주차 후 복귀 | 마지막 정산 이후 최대 8시간의 기본 전투 결과를 한 번 정산하고 자동 사냥 재개 |
| 누적 주행거리 증가 확인 | 1km마다 출발 당시 공격력 × 2의 필살기 피해. 현재 보스에게 한 번 적용, 최대 30km 반영 |
| 거리 미지원·오류·값 없음 | 기본 정산과 주차 중 사냥은 계속. 확인하지 못한 거리를 추정하지 않음 |
| 첫 조회·거리 감소·출처 변경 | 기준 거리만 새로 저장. 추가 피해 없음 |

필살기는 현재 보스의 남은 체력까지만 피해를 주며 다음 보스로 넘기지 않는다. 정산 시각·누적거리 기준·보스 체력·상자를 같은 Room 트랜잭션에 저장한다. 초과 시간과 거리를 다시 접속해 반복 지급받지 않는다. `Hero` JSON의 기본값을 사용하므로 0.2 저장 형식도 읽을 수 있다.

## 테스트 결과

| 확인 | 결과 |
|---|---|
| 전투·보상·상점·정산 규칙 | 단위 테스트 37개 통과 |
| 실제 Room 저장·동시 재시도·재시작 | 기기 테스트 7개 통과 |
| 주행/백그라운드 중단 및 복귀 UI | 기기 테스트 3개 통과. 주행 중 상태 불변, 복귀 후 필살기·자동 사냥·상자 개봉 확인 |
| 기존 상점·친구·교환 | 기기 테스트 2개 통과. 구매 화면과 두 계정 간 친구·교환·수령 확인 |
| 공식 Vehicle SDK 거리 읽기 | 기기 테스트 1개 통과. 실제 SDK에서 누적거리 0m 수신, 연결 해제 후 새 거리 수신 없음 |
| fixture/SDK debug 및 서명 release APK | 세 빌드 성공, release lint 이상 없음, APK 서명 확인 |

총 50개(단위 37개 + 기기 13개)를 통과했다. 단위 테스트 원본은 [unit-tests.json](unit-tests.json), 기기 시험 기록은 [저장](instrumentation-storage.txt)·[UI](instrumentation-ui.txt)·[상점](instrumentation-commerce.txt)·[교환](instrumentation-social.txt)·[SDK 거리 읽기](instrumentation-odometer.txt)에 있다. [실제 SDK 관측값](sdk-odometer-0.3.json)은 에뮬레이터에서 읽은 값이며 차량 값을 주입하지 않았다. 기기 시험은 기존 게임 진행을 보관하고 복원하며 앱 데이터를 초기화하지 않는다.

복귀 연출 중 정산 정보가 갱신될 때 발생한 화면 오류를 수정한 뒤 최종 APK로 화면 시험을 다시 통과했다. [복귀 화면](../../artifacts/screenshots/01-home.png), [거리 진단 화면](../../artifacts/screenshots/06-diagnostics.png), 상점·친구 화면도 직접 확인했다.

## 실제 SDK와 시연 입력

`ai.pleos.playground:Vehicle:2.3.2`의 공개 API를 사용한다.

1. Manifest에 일반 권한 `pleos.car.permission.CAR_DRIVE`를 선언한다.
2. 주차 상태와 앱 전면 상태를 확인한 뒤 `Vehicle.initialize()`와 `getOdometer()`를 호출한다.
3. `checkCurrentDistanceDrivenCapabilities()`가 `READ` 또는 `READ_WRITE`를 제공할 때 `getCurrentDistanceDriven()`으로 누적거리를 읽는다.
4. 공식 2.3.2 소스에 명시된 km를 내부 m 단위로 바꿔 이전 주차 시 기준값과 비교한다. 5초가 지난 표본은 사용하지 않는다.
5. 주행 전환 또는 앱 이탈 시 Vehicle/NaviHelper를 해제한다. 주행 중 거리 구독·위치 추적은 없다.

거리별 피해량과 주행/복귀 흐름 시험에는 명시적인 fixture 입력을 사용한다. 실제 SDK 읽기 관측과 가상 거리 보너스 시험은 구분하며, 시연 데이터를 실차 이동 기록으로 표시하지 않는다.

공식 근거: [Vehicle 개요](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission), [도메인 객체](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object), [운전자 주의 분산 가이드](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-driver-distraction-elements).

주행 중 게임 중단 요구를 기준으로 설계했다. 이 기록은 공모전 심사나 앱 마켓 승인 결과를 의미하지 않는다.

## 재현

```powershell
# 저장소 루트에서, 공식 Pleos 에뮬레이터를 5554 포트에 실행
.\tools\build_release.ps1 -AdditionalTasks @(':app-boss:assembleFixtureDebug', ':app-boss:assemblePleosDebug', ':app-boss:assembleFixtureDebugAndroidTest')
.\tools\start_social.ps1
python .\tools\verify_android.py --fixture-tests --pull-images --odometer-report
.\tools\package.ps1
```

테스트 환경은 Pleos Connect Emulator 2.1.0, API 34, x86_64, 2560×1440, 160dpi, RAM 4GB, cold boot다. 호스트 그래픽 방식에서 시험 기기가 응답하지 않아 저장 데이터를 유지하고 SwiftShader로 재시작한 뒤 최종 화면·SDK 시험을 통과했다. 앱은 minSdk 33, target/compileSdk 35, JDK 17을 사용한다.
