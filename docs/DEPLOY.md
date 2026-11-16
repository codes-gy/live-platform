# 배포 절차

운영 구성과 그렇게 정한 이유는 [ARCHITECTURE.md 5절](ARCHITECTURE.md#5-배포-구성)에 있다. 이 문서는 배포하고 확인하는 순서만 다룬다.

## 1. 운영: Heroku + Neon PostgreSQL

| 구성 | 주소 · 이름 | 이미지 |
|---|---|---|
| 프론트 `onair-live-web` | **https://onair-live-web-28fc2f0d1715.herokuapp.com** | `frontend/Dockerfile` |
| 백엔드 `onair-live-api` | https://onair-live-api-daf462a2a9af.herokuapp.com | `backend/Dockerfile` |
| DB | Neon PostgreSQL (AWS us-east-1, Heroku US 리전과 같은 지역) | — |
| 미디어 | LiveKit Cloud | — |

- 다이노: 두 앱 모두 Basic 1개, **상시 운영**(심사 기간 접속 보장). 운영 시간 예약 워크플로(`dyno-schedule.yml`)는 꺼 두었다.
- DB를 Heroku 애드온이 아닌 Neon에 둔 이유: 11-14에 Heroku Postgres 애드온이 앱에서 분리 · 삭제돼 운영이 멈춘 일이 있었다. DB를 앱 밖에 두면 앱을 정리하거나 다시 만들어도 데이터가 남는다. Neon 무료 플랜은 5분 동안 쓰지 않으면 잠들고 첫 요청에서 1초 안팎으로 깨어난다.

### 1.1 운영 설정 (백엔드, 값은 문서에 적지 않는다)

| 키 | 값 |
|---|---|
| `DB_URL` · `DB_USERNAME` · `DB_PASSWORD` | Neon 직접 연결(pooler 아님). `jdbc:postgresql://<host>/neondb?sslmode=require` |
| `JWT_SECRET` | 무작위 문자열 |
| `COOKIE_SECURE` | `true` |
| `CORS_ALLOWED_ORIGINS` | 프론트 주소. Next rewrite가 브라우저의 `Origin`을 그대로 넘기므로 필요하다 |
| `LIVEKIT_URL` · `LIVEKIT_API_KEY` · `LIVEKIT_API_SECRET` | LiveKit Cloud 키. 없으면 송출 · 시청 API가 503 `LIVEKIT_NOT_CONFIGURED` |
| `JAVA_TOOL_OPTIONS` · `SERVER_TOMCAT_THREADS_MAX` | 512MB 다이노용 메모리 설정 |

- `DB_URL`이 있으면 `docker-entrypoint.sh`는 Heroku 애드온용 `DATABASE_URL` 변환을 건너뛴다.
- LiveKit 키는 `scripts/heroku-livekit-config.sh`로 루트 `.env`에서 읽어 넣을 수 있다(값은 출력하지 않는다).

### 1.2 LiveKit 웹훅

LiveKit Cloud 대시보드 → **Settings** → **Webhooks**에 `https://onair-live-api-daf462a2a9af.herokuapp.com/api/livekit/webhook`을 등록한다. 서명 키는 운영에 넣은 것과 같은 API 키를 고른다(다르면 401 `INVALID_WEBHOOK`).

웹훅이 없어도 방송은 된다. 방송자가 탭을 그냥 닫았을 때 웹훅이 있으면 30초 뒤, 없으면 주기적 정리 작업(#34)이 1~2분 뒤에 종료한다.

### 1.3 배포

```bash
scripts/deploy-heroku.sh api   # 백엔드만
scripts/deploy-heroku.sh web   # 프론트만
scripts/deploy-heroku.sh all   # 둘 다 (api → web 순서)
```

- 필요: Docker(buildx), `heroku login`. 로컬 Docker로 linux/amd64 이미지를 만들어 Heroku 레지스트리에 올리고 release한다.
- 백엔드 코드나 DB 마이그레이션이 바뀌면 `api`를 배포한다. Flyway가 시작할 때 마이그레이션을 적용한다.
- 프론트 코드나 백엔드 주소가 바뀌면 `web`을 배포한다. `BACKEND_URL`은 빌드할 때 이미지에 들어간다.
- 배포는 develop에 머지된 코드로만 한다.
- Windows에서 빌드할 때: 셸 스크립트는 `.gitattributes`로 LF를 강제한다. CRLF로 체크아웃되면 컨테이너에서 `docker-entrypoint.sh: no such file or directory`로 기동하지 못한다.

### 1.4 배포 후 확인

```bash
curl -s https://onair-live-api-daf462a2a9af.herokuapp.com/actuator/health   # {"status":"UP"}
curl -s -o /dev/null -w "%{http_code}\n" https://onair-live-web-28fc2f0d1715.herokuapp.com/   # 200
heroku logs -a onair-live-api --tail   # 시작 오류 · R14(메모리 초과) 확인
```

기능 확인 순서:
1. README의 방송자 테스트 계정으로 로그인해 방송을 만든다.
2. PC Chrome에서 송출 화면을 열고 화면과 "탭 오디오 공유"를 고른 뒤 방송을 시작한다.
3. 휴대폰(다른 기기)에서 비로그인으로 방송 상세를 열어 영상과 소리를 확인한다.
4. 휴대폰에서 방송자 계정으로 로그인해 카메라 송출을 시작하고 PC에서 시청한다. 후면 카메라 전환도 확인한다.
5. 댓글을 쓰고 상대 화면에 바로 뜨는지 본다.
6. 송출 화면에서 방송을 종료하고 시청 화면이 "방송이 종료되었습니다"로 바뀌는지 본다.
7. 다시 방송을 시작한 뒤 송출 탭을 닫는다. 30초~2분 뒤 목록에서 종료로 바뀌는지 본다.
8. 시험 방송을 삭제한다.

11-17 확인 결과: health UP(Neon 연결 · Flyway V1~V3 적용), 테스트 로그인 · 등록 · **LiveKit Cloud 시작 → LIVE** · 종료 · 삭제, 댓글 SSE(구독 1초 안에 `:connected`, 작성 즉시 `comment` 이벤트), Playwright(가짜 카메라 · 마이크)로 카메라 송출 → 비로그인 시청자 영상 · 오디오 수신 → 종료 표시.

### 1.5 되돌리기

```bash
heroku releases -a onair-live-api            # 배포 이력
heroku releases:rollback v<번호> -a onair-live-api
```

설정 변경도 release를 하나 만든다. DB 마이그레이션은 되돌아가지 않으므로 스키마를 바꾼 배포는 롤백 전에 호환되는지 본다.

### 1.6 운영 시간 예약 (꺼 둠)

`.github/workflows/dyno-schedule.yml`은 다이노를 09:00에 켜고 20:00에 끈다(KST, 비용 절감용). 심사 기간에는 상시 운영을 위해 꺼 두었다. 다시 쓰려면 `gh workflow enable dyno-schedule.yml`.

## 2. 대안: 자체 호스팅 (Docker Compose)

Heroku 없이 한 대의 PC · 서버에서 DB까지 전부 띄우는 구성이다. 11-16에 Heroku DB가 사라졌을 때 만들어 검증했고, 11-17에 Heroku + Neon으로 운영을 되돌린 뒤에는 대안으로 남겼다.

```bash
cp .env.prod.example .env.prod   # DB 비밀번호 · JWT 비밀 키 · LiveKit 키 · PUBLIC_URL
scripts/self-host.sh up          # PostgreSQL · Spring · Next.js 빌드/시작 + Tailscale Funnel 공개
scripts/self-host.sh down        # 중지 (DB 볼륨 jiing_pgdata는 유지)
```

- `docker-compose.prod.yml`: 프론트만 `127.0.0.1:3300`에 열고, 백엔드 · DB는 내부 네트워크로만 통신한다.
- 공개는 HTTPS 리버스 프록시가 필요하다(모바일 카메라 · 화면 공유는 HTTPS에서만 동작). 스크립트는 Tailscale Funnel을 쓰고, 처음 한 번 관리 콘솔에서 Funnel을 허용해야 한다. Nginx + Let's Encrypt, Cloudflare named tunnel로 바꿔도 된다.
- **Cloudflare 임시 터널(`*.trycloudflare.com`)은 쓰지 않는다**: SSE를 지원하지 않아(공식 문서 명시, 실측 45초간 이벤트 0개) 댓글 실시간 전송이 멈추고, 다시 띄울 때마다 주소가 바뀐다.
- 11-16 확인(임시 터널): API 전 흐름과 브라우저 송출 → 시청 → 종료는 통과, 댓글 SSE만 위 이유로 실패.
- 한계: 운영 PC가 꺼지거나 Docker가 멈추면 서비스도 멈춘다.
