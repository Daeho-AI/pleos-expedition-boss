# 소스 빌드

Windows에서는 D드라이브에 작업 폴더를 준비합니다. Python 3.11 이상, 하드웨어 가상화와 인터넷 연결이 필요합니다.

```powershell
git clone https://github.com/Daeho-AI/pleos-expedition-boss.git D:\Codex\ExpeditionBoss
cd D:\Codex\ExpeditionBoss
python .\tools\bootstrap_android.py --pleos-image
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
powershell -ExecutionPolicy Bypass -File .\tools\package.ps1
```

`--pleos-image`를 생략하면 에뮬레이터 이미지를 받지 않습니다. 빌드는 전투 규칙 테스트와 시연·SDK debug APK 생성을 포함합니다.

## 서명된 APK 만들기

```powershell
python .\tools\prepare_release_signing.py
powershell -ExecutionPolicy Bypass -File .\tools\build_release.ps1
```

서명키는 로컬에 생성하며 저장소에 포함하지 않습니다. 직접 만든 키로 서명한 APK는 공개된 APK와 서명이 다릅니다.

## 개발 환경

JDK 17, Gradle 8.10.2, Android Gradle Plugin 8.8.0, Kotlin 1.9.22를 사용합니다. 앱의 minSdk는 33, compileSdk와 targetSdk는 35입니다. Pleos Vehicle SDK 2.3.2와 NaviHelper 3.0.5를 사용하며, SDK 접근 조건은 [Pleos 개발자 사이트](https://developers.pleos.ai/playground?locale=ko-KR)를 따릅니다.

[설치 문제 해결](INSTALL.md) · [친구 서버](../server/README.md) · [차량 SDK 확인 기록](validation/SDK_VALIDATION.md)
