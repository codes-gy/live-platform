# 배포 절차 (Heroku)

운영 구성과 그렇게 정한 이유는 [ARCHITECTURE.md 5절](ARCHITECTURE.md#5-배포-구성-9)에 있다. 이 문서는 배포하고 확인하는 순서만 다룬다.

| 앱 | 주소 | 이미지 |
|---|---|---|
| `onair-live-web` (프론트) | https://onair-live-web-28fc2f0d1715.herokuapp.com | `frontend/Dockerfile` |
| `onair-live-api` (백엔드) | https://onair-live-api-daf462a2a9af.herokuapp.com | `backend/Dockerfile` |

## 1. 준비물

- `heroku login`으로 로그인한 Heroku CLI
- Docker와 buildx. WSL에서는 Docker Desktop의 WSL integration을 켜야 `docker` 명령이 잡힌다.
- 루트 `.env`의 LiveKit Cloud 키 3개(`LIVEKIT_URL` · `LIVEKIT_API_KEY` · `LIVEKIT_API_SECRET`). `.env`는 커밋하지 않는다.

## 2. 운영 설정

이미 들어가 있는 값(11-12):

| 앱 | 키 | 값 |
|---|---|---|
| api | `DATABASE_URL` | Heroku Postgres 애드온이 넣음. `docker-entrypoint.sh`가 JDBC로 바꾼다 |
| api | `JWT_SECRET` | 무작위 문자열 |
| api | `COOKIE_SECURE` | `true` |
| api | `CORS_ALLOWED_ORIGINS` | 프론트 주소 |
| api | `TEST_ACCOUNTS_ENABLED` | `true` (심사용 테스트 계정) |
| api | `JAVA_TOOL_OPTIONS` · `SERVER_TOMCAT_THREADS_MAX` | 512MB 다이노용 메모리 설정 |

설정 이름만 확인하려면(값은 출력하지 않는다):

```bash
heroku config -a onair-live-api --json | node -e 'console.log(Object.keys(JSON.parse(require("fs").readFileSync(0))).join("\n"))'
```

### 2.1 LiveKit 키 넣기

```bash
scripts/heroku-livekit-config.sh
```

`.env`에서 키를 읽어 `onair-live-api`에 넣고 값은 출력하지 않는다. 설정이 바뀌면 백엔드가 재시작된다. 키가 없으면 송출 · 시청 API가 503 `LIVEKIT_NOT_CONFIGURED`를 돌려준다.

### 2.2 LiveKit 웹훅 등록

1. LiveKit Cloud 대시보드에서 프로젝트 → **Settings** → **Webhooks**로 간다.
2. URL에 `https://onair-live-api-daf462a2a9af.herokuapp.com/api/livekit/webhook`을 넣는다.
3. 서명 키는 2.1에서 넣은 것과 같은 API 키를 고른다. 다른 키로 서명하면 서버가 401 `INVALID_WEBHOOK`으로 거절한다.

웹훅이 없어도 방송은 된다. 다만 방송자가 탭을 그냥 닫았을 때 종료되기까지 더 오래 걸린다. 웹훅이 있으면 30초 뒤, 없으면 주기적 정리 작업(#34)이 약 1~2분 뒤에 종료한다.

## 3. 배포

```bash
scripts/deploy-heroku.sh api   # 백엔드만
scripts/deploy-heroku.sh web   # 프론트만
scripts/deploy-heroku.sh all   # 둘 다 (api → web 순서)
```

- 백엔드 코드나 DB 마이그레이션이 바뀌면 `api`를 배포한다. Flyway가 시작할 때 마이그레이션을 적용한다.
- 프론트 코드가 바뀌거나 백엔드 주소가 바뀌면 `web`을 배포한다. `BACKEND_URL`은 빌드할 때 이미지에 들어가므로, 백엔드 주소를 바꾸면 프론트를 다시 빌드해야 한다.
- 배포는 develop에 머지된 코드로만 한다. 배포 전에 `git switch develop && git pull`로 최신 상태를 받는다.
- 다이노가 꺼진 시간(20:00~09:00 KST)에 배포하면 release는 되지만 서버는 켜지지 않는다. 확인하려면 4.3의 명령으로 켠다.

## 4. 배포 후 확인

### 4.1 서버

```bash
curl -s https://onair-live-api-daf462a2a9af.herokuapp.com/actuator/health   # {"status":"UP"}
curl -s -o /dev/null -w "%{http_code}\n" https://onair-live-web-28fc2f0d1715.herokuapp.com/   # 200
heroku logs -a onair-live-api --tail   # 시작 오류 · R14(메모리 초과) 확인
```

### 4.2 기능

1. 테스트 계정 STREAMER로 로그인해 방송을 만든다.
2. PC Chrome에서 송출 화면을 열고 화면과 "탭 오디오 공유"를 고른 뒤 방송을 시작한다. 503이 나오면 2.1을 다시 확인한다.
3. 휴대폰(다른 기기)에서 비로그인으로 방송 상세를 열어 영상과 소리가 나오는지 본다.
4. 휴대폰에서 STREAMER로 로그인해 카메라 송출을 시작하고 PC에서 시청한다. 후면 카메라 전환도 확인한다.
5. 댓글을 쓰고 상대 화면에 바로 뜨는지 본다.
6. 송출 화면에서 방송을 종료하고 시청 화면이 "방송이 종료되었습니다"로 바뀌는지 본다.
7. 다시 방송을 시작한 뒤 송출 탭을 닫는다. 30초 정도 뒤 방송 목록에서 종료로 바뀌면 웹훅이 동작하는 것이다.
8. 시험 방송을 삭제한다.

### 4.3 운영 시간

GitHub Actions `Heroku 운영 시간`(`.github/workflows/dyno-schedule.yml`)이 매일 09:00에 켜고 20:00에 끈다(KST). 직접 켜고 끄려면:

```bash
gh workflow run dyno-schedule.yml -f action=on
gh workflow run dyno-schedule.yml -f action=off
```

심사 기간에 24시간 접속이 필요하면 워크플로를 끈다(`gh workflow disable dyno-schedule.yml`). 이때는 비용이 늘어난다.

## 5. 되돌리기

```bash
heroku releases -a onair-live-api            # 배포 이력
heroku releases:rollback v<번호> -a onair-live-api
```

설정(`config:set`)도 release를 하나 만든다. 되돌릴 때 설정까지 이전 값으로 돌아가는지 확인한다. DB 마이그레이션은 되돌아가지 않으므로, 스키마를 바꾼 배포는 롤백하기 전에 호환되는지 본다.

## 6. 남은 일

- [ ] 2.1 LiveKit 키 넣기
- [ ] 2.2 웹훅 등록
- [ ] develop 최신 코드 배포. #34 LIVE 방송 정리가 아직 운영에 없다(`scripts/deploy-heroku.sh api`).
- [ ] 4.2 기능 확인. 이것까지 끝나면 이슈 #9를 닫는다.
- [ ] 도메인 결정. `pit-live.kro.kr`은 Heroku에 추가돼 있지만 DNS CNAME이 아직 없다.
