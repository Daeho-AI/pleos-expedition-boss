# 외부 구성요소

원정 보스는 다음 외부 도구와 라이브러리를 사용한다. 각 구성요소의 저작권·라이선스·이용 조건은 해당 권리자의 공식 배포물과 문서를 따른다.

| 구성요소 | 사용 버전 / 출처 |
|---|---|
| Pleos NaviHelper | 3.0.5, [Pleos 개발자 사이트](https://developers.pleos.ai/playground?locale=ko-KR), 공식 Maven 저장소 |
| Android SDK / AndroidX / Compose / Room | API 35, Compose BOM 2024.04.01, Room 2.6.1; [Android Developers](https://developer.android.com/) |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | 1.9.22 / 1.8.1 / 1.6.3; [Kotlin](https://kotlinlang.org/) |
| Gradle wrapper / Android Gradle Plugin | 8.10.2 / 8.8.0; [Gradle](https://gradle.org/), Android Developers |
| Eclipse Temurin JDK | 17.0.21+9; [Adoptium](https://adoptium.net/) |
| Pleos Connect Emulator | 2.1.0; 현대자동차그룹 공식 개발자 배포처 |
| Uvicorn / Click / h11 | 0.51.0 / 8.3.2 / 0.16.0; [Uvicorn](https://www.uvicorn.org/), Python 친구 서버 |
| SQLite | Python 표준 라이브러리의 sqlite3; 친구·교환 데이터 저장 |

Gradle wrapper의 원래 라이선스 헤더를 보존한다. SDK·JDK·에뮬레이터 설치본과 이미지는 소스 저장소 또는 Windows 시연 ZIP에 포함하지 않는다. 설치 도구가 공식 배포처에서 내려받는다. 앱 의존 라이브러리는 Android 빌드 과정에서 APK에 포함된다.

앱의 보스와 장비 그림은 `ui/Art.kt`의 Canvas 코드로 그린다. README의 앱 화면은 이 프로젝트의 시연 앱에서 캡처한 것이다. 현대자동차그룹·Pleos·Android 명칭은 대상 플랫폼을 설명하기 위해 사용하며 공식 인증이나 제휴를 표시하지 않는다.

이 저장소는 별도의 프로젝트 오픈소스 라이선스를 아직 부여하지 않았다. 공개 열람 가능 여부와 재사용에 대한 권한 부여는 구분한다.
