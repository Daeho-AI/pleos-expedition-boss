# SDK 연결과 게임 검증

대상 환경은 **공식 Pleos Connect 2.1.0 x86_64 에뮬레이터 / API 34**다. 시연용 APK와 실제 SDK APK를 분리하고 입력 출처를 화면에 표시한다.

## 확인한 연결

- NaviHelper 3.0.5의 서비스 연결과 안내 상태 응답.
- 순정 내비 안내 시 `naviMode == ROUTE_GUIDING` 판정, 취소·도착 이벤트 처리.
- AAOS 차량 속도·P 기어·CarUxRestrictions 읽기.
- 사용자 활성화 기록 서비스의 백그라운드 콜백.
- Virtual Console에서 생성한 20km/h 입력에 대한 이동 속도 표본 수신.
- 주차 확인 후 게임 조작 화면 표시.

기록: [안내 모드](sdk-final-route-mode.json), [차량 입력 수신](sdk-final-motion.json). 기록의 수치는 에뮬레이터 입력과 SDK 수신 결과다.

## 적용한 데이터 규칙

| 데이터 | API | 처리 |
|---|---|---|
| 안내 상태 | `getRouteStateInfo()` → `onRouteStateInfo()` | 새 응답의 `ROUTE_GUIDING`, 15초 만료 |
| 도로 제한속도 | `onCurrentRoadSpeedLimit(Int)` | 양수만 사용, 3초 만료 |
| 차량 속도 | `PERF_VEHICLE_SPEED` 콜백 | m/s × 3.6, 2초 만료 |
| 기어 | `GEAR_SELECTION` | 읽기 전용 |
| 주행 화면 제한 | `CarUxRestrictionsManager` | P 기어 및 제한 해제 때 게임 조작 허용 |

에뮬레이터에서 `isRouting`과 안내 모드가 다르게 응답한 사례가 있어 명시적인 `naviMode`를 기준으로 사용한다. 안내 시뮬레이션 모드는 원정 조건에서 제외한다. 데이터가 없거나 만료되면 전투 계산을 중지하며, 차량 속도·기어·조명 제어는 수행하지 않는다.

## 재현

빌드와 실행은 [README](../../README.md)를 따른다. 친구·교환 시험에는 포함된 PC 서버를 실행한다.

```powershell
.\tools\build.ps1 -Tasks ':app-boss:assembleFixtureDebugAndroidTest'
.\tools\start_social.ps1
.\tools\run_demo.ps1 -Mode fixture
python .\tools\verify_android.py --fixture-tests --pull-images
```

기기 테스트는 시연 데이터로 수행한다. UI 테스트는 테스트 전의 진행 상태를 저장하고 종료 시 복원한다. `--reset-demo` 옵션은 진행 전체를 지우므로 일반 검증에는 사용하지 않는다.

전투·보상·상점 규칙 결과는 [unit-tests.json](unit-tests.json), 이번 기능 추가의 검증 범위는 [0.2.0 검증 기록](UPGRADE_0_2.md)에 있다.

공식 참고: [NaviHelper 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app), [Pleos 개발 가이드](https://document.pleos.ai/docs/pleos-only/getting-started).
