# 구현·SDK 검증 기록

검증일: 2026-11-06. 대상은 **공식 Pleos Connect 2.1.0 x86_64 에뮬레이터**다. 실제 차량에서 운행한 결과는 아니다.

## 현재 결론

게임의 보스 처치 → 상자 → 장비 → 도감 흐름을 실행 가능한 Android 앱으로 구현했다. 실제 SDK APK에서는 NaviHelper 서비스 연결, 안내 모드 응답, 차량 속도, P 기어 기반 화면 전환, 백그라운드 수신을 확인했다.

**도로 제한속도 콜백은 수신하지만, 테스트 위치에서는 유효한 양수 제한속도를 확인하지 못했다. 따라서 실제 SDK 입력만으로 보스를 처치하는 전체 운행 검증은 미완료다.** 이 경우 앱은 전투를 멈춘다. 제한속도를 임의의 값으로 대체하지 않는다.

## 빌드와 게임 검증

| 검증 | 결과 | 증거 |
|---|---|---|
| 실제 SDK debug APK / 시연 APK | 빌드·설치 성공 | [파일 크기·SHA-256](../../artifacts/build-manifest.json) |
| 전투·보상·장비 규칙 | 20개 통과 | [개인 PC 정보를 제외한 테스트 결과](unit-tests.json) |
| 기기 SQLite 저장 | 4개 통과 | [저장 테스트](instrumentation-storage.txt) |
| 가상 운행 → 상자 개봉 → 장비·도감·연결 화면 | 1개 통과 | [화면 테스트](instrumentation-ui.txt), `artifacts/screenshots/01-home.png`~`06-diagnostics.png` |

20개 규칙 테스트는 정상 주행의 고정 피해, 속도에 따른 보상 차등 없음, 미처치 체력 유지, 데이터 만료, 후진·정차, 과속 5초/20초 반격, 표본 단절, 제한속도 하락 유예, 운행당 2마리 한도, 중복 시간, 프로세스 재생성, 오프라인 시간 미보상, 기절, 중복 상자 지급 방지, 천장, 주차 상태, 강화 비용, 직렬화, 중복 도착 이벤트를 검사한다.

SQLite 테스트에서는 같은 상자를 동시에 10번 열어도 한 번만 지급되는지, 재시작 후 같은 강화 요청이 재결제되지 않는지, 같은 요청 ID의 다른 내용이 거절되는지, 같은 보스에 두 상자가 들어가지 못하는지 검사했다. 초기 테스트에서 Room의 일반 Upsert가 보조 고유 키 충돌을 조용히 처리하는 문제를 발견해 신규 상자 삽입과 기존 상자 갱신을 분리했다. 수정 후 4개가 모두 통과했다.

## 실제 사용한 API

| 목적 | 실제 호출·콜백 | 조건 |
|---|---|---|
| SDK 연결 | `NaviHelper(context).initialize(onConnected, onFailed)` | 공식 Maven의 3.0.5 |
| 현재 안내 여부 | `getRouteStateInfo()` → `onRouteStateInfo()` → `naviMode` | `ROUTE_GUIDING`만 허용, 응답 15초 만료 |
| 상태 전환 | `onNavigationStatus`, `onRouteCancelled`, `onDestinationArrived`, `onDestinationChanged` | 취소·안내 종료에 이전 안내 상태를 즉시 무효화 |
| 도로 제한속도 | `onCurrentRoadSpeedLimit(Int)` | 양수만 유효, 3초 만료 |
| 차량 실제 속도 | `CarPropertyManager.registerCallback(PERF_VEHICLE_SPEED)` | `CAR_SPEED`, m/s × 3.6, 2초 만료 |
| 기어 | `getProperty<Int>(GEAR_SELECTION, 0)` | `CAR_POWERTRAIN`, 읽기만 수행 |
| 화면 제한 | `CarUxRestrictionsManager.currentCarUxRestrictions`와 listener | P 기어 및 제한 해제일 때만 게임 화면 |

앱은 `NAVI_ROUTE` 읽기 권한만 선언한다. 경로 쓰기, 내비 지도 오버레이, 조명 제어, 차량 속도·기어 설정을 사용하지 않는다. `tools/console_session.py`는 공식 에뮬레이터의 시험 상태를 관리하는 PC 도구이며 APK에 포함되지 않는다.

## 현장에서 확인한 SDK 차이와 수정

### 1. isRouting은 이 에뮬레이터에서 안내 중에도 false였다

순정 내비에서 공개 장소를 검색해 안내를 시작했다. `NavigationStatus=ROUTE_DRIVE` 이벤트가 수신됐지만 `RouteStateInfo.isRouting=false`가 계속 관측됐다.

최종 앱은 현재 상태를 명확히 나타내는 `RouteStateInfo.naviMode == ROUTE_GUIDING`으로 판정한다. `SIMULATION_GUIDING`과 `SAFE_GUIDING`은 전투 조건에서 제외한다. 실제 기록 [sdk-final-route-mode.json](sdk-final-route-mode.json)에 `naviMode=ROUTE_GUIDING`, `rawIsRouting=false`, `routeGuidance=true`가 함께 남아 있다.

또한 안내 중 앱을 재시작하면 전환 이벤트가 다시 오지 않아 `navStatus=UNKNOWN`일 수 있다. 따라서 새 `RouteStateInfo`의 안내 모드 응답을 현재 상태의 기준으로 사용한다. 이 동작도 재시작 후 관측했다. 과거 이벤트 한 번만으로 안내 상태를 무기한 인정하지 않는다.

시험을 마칠 때 순정 내비에서 안내 종료를 누른 뒤 `NavigationStatus=FREE_DRIVE` 수신도 확인했다.

### 2. android.car 라이브러리 설치 선언

공식 2.1.0 이미지에는 Android 차량 클래스가 부트 클래스 경로의 `android.car-module.jar`로 제공된다. 반면 `pm list libraries`에는 `android.car`라는 공유 라이브러리 이름이 없었다. 이를 필수 공유 라이브러리로 선언한 첫 APK는 설치가 거절됐다.

컴파일은 공개 `useLibrary("android.car")`로 수행하고 설치 선언은 선택 사항으로 수정했다. 이후 동일 에뮬레이터에서 설치, `Car.createCar`, 속도 구독, 기어 조회, UX 제한 조회가 실행됐다. 차량 기능 자체는 실제 SDK 빌드에서 필수 자동차 기기로 제한한다.

### 3. 에뮬레이터 네트워크

처음에는 에뮬레이터의 DNS 자동 감지가 실패해 순정 내비가 연결 대기 상태였다. `-dns-server` 실행 옵션을 지정한 뒤 주소 조회, 내비 초기화, 목적지 검색, 경로 안내가 가능해졌다. Windows의 DNS 설정은 바꾸지 않았다.

Virtual Console에 활성 세션이 없으면 시뮬레이션 차량 데이터가 생성되지 않을 수 있다. 공식 문서대로 8088 포트를 연결하고 콘솔 세션을 활성화해야 한다.

## 데이터 수신 증거와 남은 검증

- [sdk-final-route-mode.json](sdk-final-route-mode.json): 안내 중 앱 재시작 후 명시적인 안내 모드로 현재 상태를 복원한 최종 구현의 기록.
- [sdk-final-motion.json](sdk-final-motion.json): 공식 Virtual Console에서 20km/h를 발생시켰을 때 앱이 이동 속도 표본 222개를 읽었다. 최댓값은 약 20.000001km/h였고, 실제 안내 모드 아래 운행 세션 1개가 생성됐다. 제한속도 유효값이 없어 보스 HP 180,000 milliHP와 상자 0개는 그대로 유지됐다. 시험 종료 후 가상 차량은 P·0km/h로 복원했다.

| 설계 검증 항목 | 판정 |
|---|---|
| G01 내비 상태 읽기 | 서비스 연결, 콜백, 순정 안내 모드 확인. `naviMode` 기반으로 실제 판정 수정 |
| G02 현재 도로 제한속도 | 콜백 수신 확인. **유효한 양수 값과 도로 변경 시 갱신은 추가 검증 필요** |
| G03 백그라운드 처리 | 공식 에뮬레이터에서 기록 서비스와 데이터 수신 확인. 대상 실차·장시간·심사 정책은 별도 |
| G06 주차 전용 게임 화면 | 실제 P 기어와 UX 제한 읽기 확인. 가상 주행 상태에서 게임 조작 화면 차단 확인 |
| 실제 SDK 입력의 전체 보스 처치 | **미완료. G02 검증 후 진행 가능** |

출시 판단에는 양수 제한속도가 나오는 도로 데이터 환경에서 정상 주행·과속·제한속도 변경·재연결을 시험해야 한다. 최종 실차에서도 속도 속성과 기어 속성 지원 여부, 백그라운드 실행 및 앱 심사를 확인해야 한다. 현재 APK는 그 확인을 위한 개발용 첫 버전이다.

## 재현 자료

- Kotlin 1.9.22 / Compose compiler 1.5.10 / AGP 8.8.0 / Gradle 8.10.2
- JDK 17 / compile·target API 35 / minimum API 33
- Room 2.6.1 / NaviHelper 3.0.5
- 공식 Pleos Connect Emulator 2.1.0, API 34, x86_64, 4GB RAM, Cold boot
- 실행·빌드 명령은 [README](../../README.md)에 정리했다.

기기 테스트를 다시 수행하려면 아래 명령을 실행한다. `--reset-demo`는 시연 게임의 진행을 초기화하므로 시험 기기에서만 사용한다.

```powershell
.\tools\build.ps1 -Tasks ':app-boss:assembleFixtureDebugAndroidTest'
.\tools\build.ps1 -Tasks '--stop'
.\tools\start_emulator.ps1 -Visible
python .\tools\verify_android.py --fixture-tests --reset-demo --pull-images
```

2026-11-09 공개 준비에서 별도 키로 서명한 **release APK 빌드와 서명 검사**를 추가했다. release 빌드에서도 게임/SDK 로직은 동일하나, 위 기기 테스트와 JSON 기록은 debug APK로 얻은 결과다. release의 전체 차량 운행 검증을 완료한 것으로 해석하지 않는다. [마켓 정책상 남은 확인](../CONTEST.md)도 참조한다.

확인한 공식 자료: [NaviHelper 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app), [권한 선언](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/declaring-permissions), [공식 에뮬레이터 설치](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [Virtual Console](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [차량별 API 가용성](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability).
