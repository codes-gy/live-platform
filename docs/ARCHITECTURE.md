# 아키텍처 — 지이잉(live-platform)

실시간 라이브 방송을 송출 · 시청하는 웹 서비스의 시스템 구성과 코드 구조를 정리한다.
도메인 설계(상태 · 권한 · API · 흐름)는 [`DESIGN.md`](DESIGN.md), 요구사항별 진행 상태는 [`REQUIREMENTS.md`](REQUIREMENTS.md)를 본다.

- 기준: 2026-11-12, 브랜치 `feature/live/#7-pc-broadcast`
- 표기: **(예정)** 은 아직 구현하지 않은 부분

## 1. 기술 스택

| 영역 | 선택 | 이유 |
|---|---|---|
| 프론트엔드 | Next.js 16 (App Router, TypeScript, Tailwind 4) · `livekit-client` · `@livekit/components-react` | 화면 공유 · 카메라 · WebRTC는 브라우저에서 일어나므로 SPA 성격이 강하다. `/api` rewrites로 백엔드와 같은 출처를 만들어 쿠키 인증을 단순하게 유지 |
| 백엔드 | Java 25 · Spring Boot 4.1.2 · Spring Security · JPA · Flyway · springdoc | 인증 · 인가 · 방송 상태 관리 · LiveKit 제어를 맡는 API 서버 |
| DB | PostgreSQL 18 | 방송 정보 영구 저장(재시작 후 조회). 스키마는 Flyway 마이그레이션으로 관리 |
| 스트리밍 | **LiveKit Cloud** (WebRTC SFU) | 미디어 서버를 직접 운영하지 않고 1:N 저지연 송출을 얻는다. 서버는 토큰 발급 · 방 생성/삭제 · 웹훅만 담당 |
| 테스트 | JUnit 5 · Testcontainers PostgreSQL · MockMvc · Playwright(E2E, 저장소 밖) | 실제 DB로 통합 테스트, LiveKit은 가짜 클라이언트로 대체 |
| CI | GitHub Actions | PR마다 백엔드 `./gradlew test` + 프론트 lint · build |
| 배포 | 자체 호스팅: Docker Compose(PostgreSQL · Spring · Next.js) + Tailscale Funnel (11-16~, 이전 Heroku) | 호스팅 업체 의존 없이 한 대에서 전부 실행. Funnel이 HTTPS를 붙여 준다(모바일 카메라 · 화면 공유는 보안 컨텍스트에서만 동작). 고정 주소 · SSE 지원 |

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
├── docs/                     요구사항 · 아키텍처 · 설계 문서
├── .github/                  CI(workflows/ci.yml), 이슈 · PR 템플릿
├── docker-compose.yml        로컬 PostgreSQL (+ 선택: 로컬 LiveKit dev 서버, profile livekit)
├── .env.example              환경변수 예시 (실제 .env는 커밋 금지)
├── PROGRESS.md               작업 진행 기록
└── README.md                 실행 방법 · 기능 확인 절차 (#14에서 완성)
```

### 4.1 백엔드 패키지 (`backend/src/main/java/com/gy/live`)

도메인별 패키지(package-by-feature) + 공통 `global`.

```
com.gy.live
├── auth/            회원가입 · 로그인 · 테스트 로그인 · 로그아웃 · 내 정보
│   ├── AuthController, AuthService
│   ├── TestAccount, TestAccountInitializer   심사용 계정을 시작 시 생성
│   └── dto/
├── user/            User 엔티티 · UserRepository
├── broadcast/       방송 정보와 라이브 상태
│   ├── Broadcast (엔티티 · 상태 전이 start/end/checkLive/checkDeletable)
│   ├── BroadcastStatus (READY · LIVE · ENDED)
│   ├── BroadcastController / BroadcastService            CRUD, 소유자 확인 findOwned
│   ├── BroadcastLiveController / BroadcastLiveService    start · publisher-token · end · viewer-token
│   ├── LiveKitWebhookController                          room_finished · participant_left 처리
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
    U["사용자 · 심사자<br/>PC · 휴대폰"] -- "HTTPS<br/>jiing-live.tail00a238.ts.net" --> TS["Tailscale Funnel<br/>(TLS 종료 · 공개)"]
    subgraph Host["운영 PC (Docker Compose, docker-compose.prod.yml)"]
        FE["frontend<br/>Next.js standalone<br/>127.0.0.1:3300"]
        BE["backend<br/>Spring Boot"]
        PG[("postgres<br/>볼륨 jiing_pgdata")]
    end
    TS --> FE
    FE -- "/api rewrites<br/>http://backend:8080" --> BE
    BE --> PG
    BE <-- "RoomService · 웹훅" --> LK["LiveKit Cloud"]
    U <-- "WebRTC (wss · UDP/TCP)" --> LK
```

- 서비스: https://jiing-live.tail00a238.ts.net. 시작 · 확인 순서는 [DEPLOY.md](DEPLOY.md).
- **왜 자체 호스팅인가**: 11-14에 Heroku 운영 DB가 삭제된 것을 발견했고, 외부 호스팅 의존을 줄이기로 했다(11-16). 이미지는 Heroku 때와 같은 `backend/Dockerfile` · `frontend/Dockerfile`이고, compose가 DB까지 함께 띄운다.
- **왜 Tailscale Funnel인가**: 계정 없는 Cloudflare 임시 터널은 SSE를 지원하지 않아(댓글 실시간 전송이 멈춤) 쓸 수 없었고 재시작마다 주소가 바뀐다. Funnel은 도메인 없이 고정 주소 · 자동 인증서를 주고 스트리밍 응답을 그대로 흘린다.
- 노출 범위: 프론트만 `127.0.0.1:3300`에 열고 Funnel이 그 포트를 공개한다. 백엔드 · DB는 호스트 포트가 없고 compose 내부 네트워크로만 통신한다. 브라우저는 같은 출처의 `/api`만 부른다.
- 백엔드는 PC 메모리에 맞춰 `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60`(Heroku 512MB용 제한 해제). `COOKIE_SECURE=true`, `CORS_ALLOWED_ORIGINS`는 공개 주소(`scripts/self-host.sh`가 Tailscale에서 읽어 반영). Next rewrite가 브라우저의 `Origin`을 그대로 넘기므로 CORS 허용 목록에 공개 주소가 있어야 POST가 통과한다.
- 셸 스크립트는 `.gitattributes`로 LF를 강제한다(Windows 체크아웃의 CRLF 때문에 컨테이너에서 `docker-entrypoint.sh`가 실행되지 않았다).
- 한계: 운영 PC가 꺼지거나 절전하면 서비스가 멈춘다. 서버 1대 전제는 기존과 같다(9절 · DESIGN 9절).
- 이전 Heroku 구성(11-12~11-16, 컨테이너 앱 2개 + Postgres Essential-0, 운영 시간 예약)은 DEPLOY.md 2절에 기록으로 남겼다. 운영 시간 예약 워크플로는 꺼 두었다.
- 녹화 **(예정, #13)**: LiveKit Egress → S3 호환 스토리지.

## 6. 설정 · 환경변수

| 키 | 기본값(로컬) | 설명 |
|---|---|---|
| `DB_URL` · `DB_USERNAME` · `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/live` · `live` · `live` | PostgreSQL |
| `JWT_SECRET` · `JWT_ACCESS_TOKEN_TTL` | 개발용 문자열 · `12h` | 인증 토큰 |
| `COOKIE_SECURE` | `false` | 운영(HTTPS)에서는 `true` |
| `TEST_ACCOUNTS_ENABLED` · `TEST_ACCOUNT_PASSWORD` | `true` · `test1234!` | 심사용 계정 자동 생성 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 직접 호출 허용 출처 |
| `LIVEKIT_URL` · `LIVEKIT_API_KEY` · `LIVEKIT_API_SECRET` | 비어 있음 | 비어 있으면 라이브 API가 503 `LIVEKIT_NOT_CONFIGURED` |
| `BACKEND_URL` (프론트) | `http://localhost:8080` | `/api` rewrites 대상, **빌드 시점**에 읽힘 |

고정 설정(`app.livekit.*`): 빈 방 유지 `empty-timeout` 2분 · 마지막 참가자 이탈 후 `departure-timeout` 1분 · 방송자 이탈 유예 `broadcaster-grace` 30초 · LIVE로 남은 방송 정리 주기 `reconcile-interval` 1분(#34) · 토큰 수명 `token-ttl` 1시간.
