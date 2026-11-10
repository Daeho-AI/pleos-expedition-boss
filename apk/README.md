# APK

[공모전 저장소 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)에 맞춰 실제 SDK를 사용하는 서명된 release 빌드를 제공한다.

- [Daeho-AI_ExpeditionBoss_release.apk](Daeho-AI_ExpeditionBoss_release.apk): 버전 0.2.0, Pleos/AAOS용, `dev.pleos.entry.boss`, 디버깅 비활성화.
- [release-manifest.json](release-manifest.json): 크기와 SHA-256.
- [전체 다운로드](https://github.com/Daeho-AI/pleos-expedition-boss/releases/tag/v0.2.0-preview.1): Windows 시연 ZIP, 시연 APK, SDK 진단용 debug APK.

게임 체험에는 시연 APK를 사용한다. 친구 서버는 ZIP에 소스로 포함되며 `setup.cmd`가 실행 환경을 준비한다. release APK에서 외부 친구 서버를 연결할 때는 HTTPS 주소를 사용한다.

release와 SDK debug는 서명이 달라 서로 덮어쓸 수 없다. 같은 종류의 0.1.0 → 0.2.0 업데이트는 같은 서명키와 저장 데이터 마이그레이션을 사용한다. 실행기는 기존 게임 데이터를 자동으로 삭제하지 않는다.
