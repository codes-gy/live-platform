# 아키텍처 — 지이잉(live-platform)

실시간 라이브 방송을 송출 · 시청하는 웹 서비스의 시스템 구성과 코드 구조를 정리한다.
도메인 설계(상태 · 권한 · API · 흐름)는 [`DESIGN.md`](DESIGN.md), 요구사항별 진행 상태는 [`REQUIREMENTS.md`](REQUIREMENTS.md)를 본다.

- 기준: 2026-11-19, 브랜치 `main`
- 표기: **(미구현)** 은 설계만 있고 구현하지 않은 선택 기능

## 1. 기술 스택

| 영역 | 선택 | 이유 |
|---|---|---|
| 프론트엔드 | Next.js 16 (App Router, TypeScript, Tailwind 4) · `livekit-client` · `@livekit/components-react` | 화면 공유 · 카메라 · WebRTC는 브라우저에서 일어나므로 SPA 성격이 강하다. `/api` rewrites로 백엔드와 같은 출처를 만들어 쿠키 인증을 단순하게 유지 |
| 백엔드 | Java 25 · Spring Boot 4.1.2 · Spring Security · JPA · Flyway · springdoc | 인증 · 인가 · 방송 상태 관리 · LiveKit 제어를 맡는 API 서버 |
| DB | PostgreSQL 18 | 방송 정보 영구 저장(재시작 후 조회). 스키마는 Flyway 마이그레이션으로 관리 |
| 스트리밍 | **LiveKit Cloud** (WebRTC SFU) | 미디어 서버를 직접 운영하지 않고 1:N 저지연 송출을 얻는다. 서버는 토큰 발급 · 방 생성/삭제 · 웹훅만 담당 |
| 테스트 | JUnit 5 · Testcontainers PostgreSQL · MockMvc · Playwright(E2E, `frontend/e2e`, 실행 중인 서버 대상 수동 실행) | 실제 DB로 통합 테스트, LiveKit은 가짜 클라이언트로 대체 |
| CI | GitHub Actions | PR마다 백엔드 `./gradlew test` + 프론트 lint · build |
| 배포 | Heroku 컨테이너(프론트 · 백엔드 앱 2개, Basic 다이노) + **Neon PostgreSQL** (#9). 대안: Docker Compose 자체 호스팅 | HTTPS 자동(모바일 카메라 · 화면 공유는 보안 컨텍스트에서만 동작). DB를 앱 밖에 두어 앱을 다시 만들어도 데이터 유지. GitHub 학생 크레딧 사용 |

## 2. 시스템 구성도

```mermaid
flowchart LR
    subgraph Client["브라우저"]
        Streamer["방송자<br/>PC: 화면 공유 + 탭/시스템 소리 + 마이크<br/>모바일: 카메라 + 마이크"]
        Viewer["시청자<br/>(비로그인 가능)"]
    end

    subgraph Front["Next.js (frontend/)"]
        Pages["페이지 · 컴포넌트"]
        Proxy["proxy.ts<br/>보호 경로 → /login"]
        Rewrite["rewrites<br/>/api/* → BACKEND_URL"]
    end

    subgraph Back["Spring Boot (backend/)"]
        Sec["Security<br/>JWT 쿠키 필터"]
        API["REST API<br/>auth · broadcast · live · comment"]
        Hook["LiveKit 웹훅 수신"]
        SSE["댓글 SSE"]
    end

    DB[("PostgreSQL<br/>users · broadcasts · comments")]
    LK["LiveKit Cloud<br/>(WebRTC SFU)"]

    Streamer -- HTTPS --> Pages
    Viewer -- HTTPS --> Pages
    Pages --> Rewrite --> Sec --> API
    API --> DB
    SSE -. 댓글 이벤트 .-> Viewer
    API -- "RoomService<br/>CreateRoom · DeleteRoom · 참가자 조회" --> LK
    LK -- "웹훅 (서명 JWT)<br/>room_finished · participant_left" --> Hook
    Hook --> DB

    Streamer == "WebRTC 송출<br/>(publish 토큰)" ==> LK
    LK == "WebRTC 수신<br/>(subscribe 토큰)" ==> Viewer
```

핵심은 **제어와 미디어를 나눈 것**이다.

- **제어 경로**(얇은 선): 브라우저 → Next.js → Spring. 로그인, 방송 CRUD, 송출 시작 · 종료, 토큰 발급처럼 권한 판단이 필요한 일은 모두 Spring을 거친다.
- **미디어 경로**(굵은 선): 브라우저 ↔ LiveKit. 영상 · 음성은 Spring을 지나지 않는다. Spring이 발급한 토큰에 담긴 권한(방 이름 · 송출 가능 여부)만 LiveKit이 확인한다.
- 따라서 "누가 송출할 수 있는가"는 Spring이 토큰 발급 시점에 결정하고, "송출을 강제로 끊는다"는 Spring이 LiveKit 방을 삭제해서 처리한다.

## 3. 요청 경로와 출처

```mermaid
flowchart LR
    B["브라우저<br/>https://서비스"] -- "/, /broadcasts/..., /studio/..." --> N["Next.js"]
    B -- "/api/** (같은 출처, 쿠키 포함)" --> N
    N -- "rewrites" --> S["Spring :8080"]
    B -- "wss://*.livekit.cloud" --> L["LiveKit Cloud"]
```

- 브라우저는 항상 **같은 출처의 `/api/**`** 만 호출한다. Next.js가 `BACKEND_URL`로 넘기므로 CORS와 서드파티 쿠키 문제를 피한다.
- 인증 쿠키 `access_token`은 HttpOnly · SameSite=Lax(운영은 Secure)다. CSRF 토큰 대신 SameSite로 막는다.
- LiveKit 접속은 백엔드가 응답한 `serverUrl`(wss)로 브라우저가 직접 연결한다.

## 4. 저장소 구조

```
live-platform/
├── backend/                  Spring Boot API 서버
├── frontend/                 Next.js 웹 앱
├── frontend/e2e/             Playwright 브라우저 E2E (실행 중인 서버 대상)
├── docs/                     요구사항 · 아키텍처 · 설계 · 배포 문서
├── scripts/                  deploy-heroku.sh · heroku-livekit-config.sh · self-host.sh
├── .github/                  CI(workflows/ci.yml) · 운영 시간 예약(dyno-schedule.yml, 꺼 둠), 이슈 · PR 템플릿
├── docker-compose.yml        로컬 PostgreSQL (+ 선택: 로컬 LiveKit dev 서버, profile livekit)
├── docker-compose.prod.yml   자체 호스팅용 PostgreSQL · Spring · Next.js (대안 배포)
├── .env.example              로컬 환경변수 예시 (실제 .env는 커밋 금지)
├── .env.prod.example         자체 호스팅 환경변수 예시
├── PROGRESS.md               작업 진행 기록
├── LICENSE                   MIT
└── README.md                 서비스 주소 · 테스트 계정 · 실행 방법 · 기능 확인 절차
```

### 4.1 백엔드 패키지 (`backend/src/main/java/com/gy/live`)

도메인별 패키지(package-by-feature) + 공통 `global`.

```
com.gy.live
├── auth/            회원가입 · 로그인 · 로그아웃 · 내 정보
│   ├── AuthController, AuthService
│   └── dto/
├── user/            User 엔티티 · UserRepository
├── broadcast/       방송 정보와 라이브 상태
│   ├── Broadcast (엔티티 · 상태 전이 start/end/checkLive/checkDeletable)
│   ├── BroadcastStatus (READY · LIVE · ENDED)
│   ├── BroadcastController / BroadcastService            CRUD, 소유자 확인 findOwned
│   ├── BroadcastLiveController / BroadcastLiveService    start · publisher-token · end · viewer-token
│   ├── LiveKitWebhookController                          room_finished · participant_left 처리
│   ├── LiveReconcileScheduler                            1분마다 LIVE 방송을 LiveKit과 대조(reconcileLive, #34)
│   ├── LiveBroadcastRef                                  정리 대상 조회용 (id, ownerId)
│   ├── BroadcastEndedEvent                               종료 시 발행(댓글 SSE 종료 등)
│   └── dto/
├── comment/         방송별 댓글 + SSE
│   ├── CommentController, CommentService, CommentRepository, Comment
│   ├── CommentEvent       커밋 뒤(AFTER_COMMIT) SSE로 전파
│   └── CommentStreams     방송별 SseEmitter 구독 목록(메모리)
├── livekit/         외부 시스템 경계(LiveKit)
│   ├── LiveKitRoomClient (인터페이스) ← SdkLiveKitRoomClient (테스트는 Fake로 대체)
│   ├── LiveKitTokenIssuer     송출/시청 토큰과 권한(grant)
│   ├── LiveKitWebhookVerifier 웹훅 서명 · 본문 해시 검증
│   ├── RoomNames              방 · 참가자 이름 규칙
│   └── LiveKitConfig, LiveKitException
└── global/
    ├── config/     AppProperties(app.*), PasswordConfig
    ├── security/   SecurityConfig, JwtProvider, JwtAuthenticationFilter, AuthCookies, LoginUser, SecurityErrorHandlers
    ├── error/      ErrorCode, BusinessException, ErrorResponse{code,message}, GlobalExceptionHandler
    └── web/        PageResponse
```

의존 방향:

```mermaid
flowchart TD
    auth --> user
    broadcast --> user
    broadcast --> livekit
    comment --> broadcast
    comment --> user
    auth & broadcast & comment & livekit --> global
```

- `livekit` 패키지는 도메인을 모른다(방 이름 규칙 · 토큰 · 방 API만). LiveKit SDK는 이 패키지 안에만 있다.
- `comment`는 `broadcast`의 `BroadcastEndedEvent`를 구독할 뿐, 방송 쪽은 댓글을 모른다.

DB 마이그레이션: `resources/db/migration/V1__create_users.sql` · `V2__create_broadcasts.sql` · `V3__create_comments.sql` (`ddl-auto: validate`).

### 4.2 프론트엔드 구조 (`frontend/src`)

```
src/
├── proxy.ts                       (Next 16의 middleware) 보호 경로에 쿠키 없으면 /login?next=
├── app/                           라우트
│   ├── layout.tsx                 AuthProvider · Header · Pretendard 글꼴(CDN)
│   ├── page.tsx                   / 방송 목록
│   ├── login/ · signup/           로그인 · 회원가입
│   ├── my/                        내 계정 · 내 방송 목록                        [로그인]
│   ├── broadcasts/new/            방송 등록                                     [로그인]
│   ├── broadcasts/[id]/           방송 상세 · 시청                              [공개]
│   ├── broadcasts/[id]/edit/      방송 수정                                     [소유자]
│   └── studio/[id]/               송출 스튜디오(PC 화면 공유 · 카메라)        [소유자]
├── components/
│   ├── auth/       AuthProvider(useAuth) · RequireAuth · LoginForm · SignupForm
│   ├── broadcast/  BroadcastList · BroadcastCard · BroadcastDetail · LivePlayer · BroadcastForm
│   │               CreateBroadcast · EditBroadcast · DeleteBroadcast · MyBroadcasts · OwnerActions · StatusBadge
│   │               OwnerAvatar · useViewerCount(시청자 수)
│   ├── comment/    CommentPanel · CommentComposer · CommentItem · useComments(목록 리듀서 + SSE)
│   ├── studio/     Studio(UI · 댓글 패널 · 마이크 음량) · useBroadcastSession(연결 상태 머신) · media(화면 · 카메라 · 마이크 캡처) · useWakeLock
│   └── Header · Logo · ui
└── lib/
    ├── api.ts          fetch 래퍼 api<T>(), ApiError{status, code, message}
    ├── broadcast.ts    타입 · 상태 라벨 · 시간 포맷 · loadBroadcast
    ├── comments.ts     댓글 타입 · 리듀서 · 조회
    └── navigation.ts   safeNext(오픈 리다이렉트 방지) · loginPath
```

화면 이동:

```mermaid
flowchart LR
    Home["/ 목록"] --> Detail["/broadcasts/:id<br/>상세 · 시청"]
    Home --> Login["/login"]
    Login --> My["/my 내 방송"]
    My --> New["/broadcasts/new"]
    New --> Detail
    Detail -- 소유자 --> Edit["/broadcasts/:id/edit"]
    Detail -- 소유자 --> Studio["/studio/:id<br/>송출"]
    Studio -- 종료 --> Detail
```

## 5. 배포 구성

```mermaid
flowchart LR
    U["사용자 · 심사자<br/>PC · 휴대폰"] -- HTTPS --> FE["Heroku onair-live-web<br/>(Next.js standalone 컨테이너)"]
    FE -- "/api rewrites" --> BE["Heroku onair-live-api<br/>(Spring Boot 컨테이너)"]
    BE -- "JDBC (TLS)" --> PG[("Neon PostgreSQL<br/>AWS us-east-1")]
    BE <-- "RoomService · 웹훅" --> LK["LiveKit Cloud"]
    U <-- "WebRTC (wss · UDP/TCP)" --> LK
```

- 서비스: https://onair-live-web-28fc2f0d1715.herokuapp.com (백엔드: https://onair-live-api-daf462a2a9af.herokuapp.com). 배포 · 확인 · 되돌리기는 [DEPLOY.md](DEPLOY.md).
- 앱 2개 모두 Heroku 컨테이너 스택(US 리전), Basic 다이노(메모리 512MB, 잠들지 않음), **상시 운영**. 이미지는 `backend/Dockerfile` · `frontend/Dockerfile`, 배포는 `scripts/deploy-heroku.sh [api|web|all]`(로컬 Docker로 linux/amd64 빌드 → Heroku 레지스트리 푸시 → release).
  - Heroku 레지스트리는 Docker v2 매니페스트만 받는다. buildx 기본 OCI 형식 · 출처 증명은 `unsupported`로 거절 → `oci-mediatypes=false`, `--provenance=false`.
  - 프론트는 `output: "standalone"`. `BACKEND_URL`은 빌드 인자로 넣는다(rewrites가 빌드 시점에 읽음).
  - 512MB 안에 들도록 `JAVA_TOOL_OPTIONS`(힙 45% · SerialGC · 코드 캐시 48m · 메타스페이스 160m · C1만) + Tomcat 스레드 40. 처음 설정(힙 70%)은 R14 메모리 초과가 났다.
- **DB는 Neon**(11-17~): 처음에는 Heroku Postgres 애드온을 썼으나 11-14에 애드온이 앱에서 분리 · 삭제돼 운영이 멈췄다. DB를 앱 밖(Neon, Heroku와 같은 us-east-1)에 두고 `DB_URL` · `DB_USERNAME` · `DB_PASSWORD`를 직접 넣는다(`docker-entrypoint.sh`는 `DB_URL`이 있으면 `DATABASE_URL` 변환을 건너뜀). 무료 플랜은 5분 유휴 시 잠들고 첫 요청에서 1초 안팎으로 깨어난다.
- 운영 환경변수(백엔드): `DB_*`, `JWT_SECRET`(무작위), `COOKIE_SECURE=true`, `CORS_ALLOWED_ORIGINS`(프론트 주소 — Next rewrite가 브라우저 `Origin`을 그대로 넘기므로 필요), `JAVA_TOOL_OPTIONS`, `SERVER_TOMCAT_THREADS_MAX`, `LIVEKIT_*`.
- LiveKit Cloud 웹훅 URL: `https://onair-live-api-daf462a2a9af.herokuapp.com/api/livekit/webhook`.
- 댓글 SSE는 Heroku 라우터 · Next rewrite를 거쳐도 버퍼링 없이 도착한다(구독 1초 안에 `:connected`, 20초 `:ping`이 라우터 유휴 제한 55초보다 짧음).
- 운영 시간 예약(`.github/workflows/dyno-schedule.yml`, 09~20시 KST)은 심사 기간 상시 운영을 위해 꺼 두었다.
- **대안: 자체 호스팅** — `docker-compose.prod.yml`(PostgreSQL · Spring · Next.js, 프론트만 `127.0.0.1:3300`) + `scripts/self-host.sh`(Tailscale Funnel로 HTTPS 공개). 호스팅 업체 없이 한 대에서 전부 띄울 수 있다. Cloudflare 임시 터널은 SSE를 지원하지 않아 제외했다(DEPLOY.md 2절).
- 셸 스크립트는 `.gitattributes`로 LF를 강제한다(Windows 체크아웃의 CRLF 때문에 컨테이너에서 entrypoint가 실행되지 않았다).
- 녹화 **(미구현, #13)**: 설계는 LiveKit Egress → S3 호환 스토리지([DESIGN.md](DESIGN.md) 7.2절).

## 6. 설정 · 환경변수

| 키 | 기본값(로컬) | 설명 |
|---|---|---|
| `DB_URL` · `DB_USERNAME` · `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/live` · `live` · `live` | PostgreSQL |
| `JWT_SECRET` · `JWT_ACCESS_TOKEN_TTL` | 개발용 문자열 · `12h` | 인증 토큰 |
| `COOKIE_SECURE` | `false` | 운영(HTTPS)에서는 `true` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 직접 호출 허용 출처 |
| `LIVEKIT_URL` · `LIVEKIT_API_KEY` · `LIVEKIT_API_SECRET` | 비어 있음 | 비어 있으면 라이브 API가 503 `LIVEKIT_NOT_CONFIGURED` |
| `BACKEND_URL` (프론트) | `http://localhost:8080` | `/api` rewrites 대상, **빌드 시점**에 읽힘 |

고정 설정(`app.livekit.*`): 빈 방 유지 `empty-timeout` 2분 · 마지막 참가자 이탈 후 `departure-timeout` 1분 · 방송자 이탈 유예 `broadcaster-grace` 30초 · LIVE로 남은 방송 정리 주기 `reconcile-interval` 1분(#34) · 토큰 수명 `token-ttl` 1시간.
