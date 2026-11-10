# 친구·교환 서버

닉네임과 친구 코드로 사용자를 연결하고, 교환함의 장비를 보관한다. SQLite 트랜잭션에서 두 장비의 소유권을 함께 바꾸므로 한쪽만 지급되는 교환을 막는다.

## Windows 시연

프로젝트 루트의 `setup.cmd`가 서버 실행 환경을 준비한다. 이후 `demo.cmd`가 가상 기기와 서버를 함께 실행한다. 서버만 켜려면 `social-server.cmd`를 사용한다.

- PC 주소: `http://127.0.0.1:8765`
- 같은 PC의 Android 에뮬레이터 주소: `http://10.0.2.2:8765`
- 서버 저장 파일: `D:\Codex\PleosBossToolchain\social-data\social.sqlite`
- 서버 로그: 같은 폴더의 `server.log`, `server-error.log`

두 사용자 모두 같은 서버 주소를 입력해야 한다. 친구 코드만으로 서로 다른 서버가 연결되지는 않는다. 시연용 앱과 SDK debug 앱은 별도 설치 공간이므로 같은 에뮬레이터에서 두 프로필을 만들 수도 있다. SDK 앱은 차량의 주차 상태와 필요한 권한을 확인한 뒤 이용한다.

서버가 꺼져 있어도 원정과 상점은 사용할 수 있다. 교환함 장비와 전송 대기 기록은 그대로 남으며 서버를 다시 켠 뒤 **새로고침**으로 이어서 처리한다. 서버 DB를 삭제하면 친구·교환함 데이터가 사라지므로 업데이트 시 보존한다.

## 다른 기기와 연결

기본 실행기는 PC 내부에서만 접속을 받는다. 다른 개발 기기로 시험하려면 서버를 해당 네트워크에 연결해야 한다. 아래 명령은 저장소 폴더의 PowerShell에서 실행한다. 기존 8765 서버가 실행 중이면 먼저 해당 서버를 종료하거나 다른 포트를 사용한다.

```powershell
$env:BOSS_SOCIAL_DB = 'D:\Codex\PleosBossToolchain\social-data\social.sqlite'
& 'D:\Codex\PleosBossToolchain\social-env\Scripts\python.exe' -m uvicorn app:app --app-dir .\server --host 0.0.0.0 --port 8766 --lifespan off --ws none --no-access-log
```

개발용 debug APK는 사설 IP의 HTTP 주소를 허용한다. release APK는 HTTPS 주소를 사용한다. Windows 방화벽과 외부 호스팅 설정은 자동 변경하지 않는다.

## 외부 서버 배포

Python 3.11 이상에서 `requirements.txt`를 설치한 뒤 `app:app`을 Uvicorn으로 실행할 수 있다. 또는 `server/`를 Docker 빌드 경로로 사용한다.

```sh
docker build -t expedition-boss-social ./server
docker volume create expedition-boss-data
docker run -d --name expedition-boss-social --restart unless-stopped -p 127.0.0.1:8765:8765 -v expedition-boss-data:/data expedition-boss-social
```

배포 서버에서 HTTPS를 제공하는 프록시를 앞에 두고, 앱에는 그 HTTPS 기본 주소를 입력한다. 도메인·호스팅·TLS 발급은 운영 환경에서 설정한다. 현재 GitHub 배포본에 외부 운영 서버 계정이나 비용은 포함하지 않는다. [Uvicorn 배포 문서](https://www.uvicorn.org/deployment/)를 참고한다.

## 저장과 복구

- 장비를 맡길 때 앱이 먼저 가방에서 제외하고 전송 ID를 Room에 저장한다. 서버에 같은 ID를 여러 번 보내도 장비는 한 개만 생긴다.
- 전송 취소를 먼저 확인한 ID는 서버에 취소 기록을 남겨 늦게 도착한 전송이 다시 지급되지 않게 한다.
- 교환 제안은 제안자의 장비만 잠근다. 상대의 장비를 동의 없이 묶지 않는다.
- 상대가 수락하면 서버가 친구 관계와 두 장비 소유권을 다시 확인하고 한 번의 트랜잭션에서 바꾼다. 동시에 들어온 다른 제안은 같은 장비를 중복 사용하지 못한다.
- 가방으로 받기는 서버의 수령 대기함과 앱의 지급 영수증을 사용한다. 앱 재시작이나 응답 유실이 있어도 같은 지급을 반복하지 않는다.
- 제안은 24시간 후 만료된다. 요청 처리 시 만료된 잠금을 해제한다.

사용자 식별 토큰은 앱 내부 저장소에, 서버에는 그 해시만 저장한다. 앱 삭제 시 이 기기의 프로필 연결 정보도 삭제된다. 현재 버전에는 이메일 로그인·계정 복구 기능이 없다.

교환함 안의 소유권과 거래는 서버에서 검증한다. 원정 보상의 최초 획득은 클라이언트가 신고하는 구조이므로 변조된 앱의 가짜 장비 생성까지 막는 운영용 경제 검증은 포함하지 않는다. 현금 결제나 외부 현금 거래는 없다.

## API

`/health`를 제외한 요청은 `Authorization: Bearer <기기 토큰>`을 사용한다. 토큰·DB·로그는 Git에 포함하지 않는다.

| 경로 | 기능 |
|---|---|
| `GET /health` | 서비스 이름·버전 |
| `POST /v1/profile` | 닉네임과 기기 프로필 등록 |
| `GET /v1/state` | 자신의 친구·요청·교환함·교환·수령 대기함 |
| `POST /v1/friends/request` | 친구 코드로 요청 |
| `POST /v1/requests/{id}/accept·decline·cancel` | 친구 요청 처리 |
| `POST /v1/friends/{id}/remove` | 친구 삭제 및 대기 교환 취소 |
| `POST /v1/deposits` | 장비 맡기기, 동일 ID 중복 방지 |
| `POST /v1/deposits/{id}/abort` | 전송 취소 여부 확정 |
| `POST /v1/trades` | 두 장비를 지정해 교환 제안 |
| `POST /v1/trades/{id}/accept·decline·cancel` | 교환 처리 |
| `POST /v1/items/{id}/withdraw` | 가방 수령 대기함으로 이동 |
| `POST /v1/deliveries/{id}/ack` | 기기에 저장된 지급 확인 |

테스트: 저장소 폴더에서 `python -m unittest discover -s server/tests -v`. 실행 시 임시 폴더는 작업 드라이브에 지정한다.
