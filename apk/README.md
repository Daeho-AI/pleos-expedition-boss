# APK

공모전의 [저장소 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)에 맞춰 실제 SDK를 사용하는 서명된 **release 빌드**를 이 폴더에 둔다.

- [Daeho-AI_ExpeditionBoss_release.apk](Daeho-AI_ExpeditionBoss_release.apk): Pleos/AAOS용, `dev.pleos.entry.boss`, 디버깅 비활성화.
- [release-manifest.json](release-manifest.json): 파일 크기와 SHA-256.
- [전체 다운로드](https://github.com/Daeho-AI/pleos-expedition-boss/releases/tag/v0.1.0-preview.1): Windows 시연 묶음, 시연 APK, SDK 진단용 debug APK도 제공.

`release`는 Android 빌드 종류다. 현재 앱은 개발 중인 프로토타입이며 실차 검증·마켓 심사를 완료했다는 뜻이 아니다. 제한속도의 유효값이 없으면 실제 SDK 버전은 전투를 진행하지 않는다. 게임 체험에는 **시연 APK**를 사용한다.

release APK는 debug APK와 서명이 다르다. 같은 패키지의 debug 앱이 설치된 기기에 덮어쓸 수 없다. 새 테스트 기기를 사용하거나, 필요한 게임 데이터를 보존한 뒤 사용자가 기존 앱을 제거해야 한다. 실행기는 이를 자동으로 제거하지 않는다.
