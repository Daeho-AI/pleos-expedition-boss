# 설치 파일

**[모아_원정보스_release.apk 내려받기](모아_원정보스_release.apk)**

공식 제출 형식인 `팀명_앱이름_release.apk`에 맞춘 파일입니다. Pleos Connect에서 실행하며, 실제 Vehicle SDK와 NaviHelper를 사용합니다.

```powershell
adb install -r .\apk\모아_원정보스_release.apk
```

- 앱 이름: **원정 보스**
- 패키지: `dev.pleos.entry.boss`
- 서명된 release 빌드, 디버깅 비활성화
- 영문 파일 `Daeho-AI_ExpeditionBoss_release.apk`도 같은 내용과 서명입니다.

[실행 방법과 테스트 환경](../README.md#실행-방법) · [파일 크기·SHA-256](release-manifest.json)

가상 주행으로 게임을 체험하려면 [Windows 시연 ZIP](https://github.com/Daeho-AI/pleos-expedition-boss/releases/download/v0.7.0-preview.1/ExpeditionBoss-Windows-Demo.zip)을 사용합니다. 친구 교환은 별도 서버 연결이 필요하며, 제출용 앱은 HTTPS 주소를 사용합니다. [서버 안내](../server/README.md)

SDK debug 앱과 release 앱은 서명이 다르므로 같은 기기에 서로 덮어쓸 수 없습니다.
