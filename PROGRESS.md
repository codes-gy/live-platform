# PROGRESS — live-platform (실시간 방송 플랫폼 공모전)

- 마감: **2026-11-19(목) 23:59** · 제출물: 공개 GitHub 저장소 URL 1회
- 평가 우선순위: 필수 기능 완결성 > 인증·인가 > 코드 품질·방송 상태 관리 > README > 선택 기능 가산점

## 확정 결정 사항 (2026-11-05)
| 항목 | 결정 |
|---|---|
| 폴더 | `/Users/podojjang/project/live-platform` (모노레포: `backend/`, `frontend/`) |
| 백엔드 | Java 25 · Spring Boot 4.1.2 · JPA · PostgreSQL 18 · Flyway · Spring Security + JWT · springdoc. Rust 전환 검토 후 **Spring 유지** 결정 |
| 프론트 | Next.js 16.4.5 (App Router, TS, Tailwind 4) · livekit-client · @livekit/components-react |
| 스트리밍 | **LiveKit Cloud** (WebRTC SFU). Spring은 토큰 발급·소유자 권한 검증·RoomService(송출 종료)·웹훅 처리 담당 |
| 배포 | EC2 1대 + Docker Compose + Nginx + HTTPS(Let's Encrypt). 녹화는 LiveKit Egress → S3 |
| 선택 기능 | 댓글 · 도네이션(토스페이먼츠 테스트 모드) · 녹화/다시보기 **모두 구현** (필수 기능 완료 후) |
| 테스트 | JUnit + Testcontainers PostgreSQL (Docker 필요) |

## 설계 메모 (구현 시 지킬 것)
- 방송 상태: `READY → LIVE → ENDED`. 송출 권한(publish 토큰)은 **소유자 + LIVE 전환 시점**에만 발급, 시청 토큰은 비로그인도 발급(subscribe only).
- 방송 종료 = DB 상태 ENDED + LiveKit `deleteRoom`(송출 강제 중단). 웹훅 `room_finished`/`participant_left`로 비정상 종료도 정리.
- PC 송출: `getDisplayMedia({ video, audio: true, systemAudio: 'include' })` + 마이크 트랙을 별도 퍼블리시(또는 믹싱). 탭 소리는 Chrome 탭 공유에서 보장, 시스템 소리는 Windows Chrome/Edge만 지원 → README 지원 환경에 명시.
- 모바일 송출: `getUserMedia({ video: { facingMode }, audio })`. HTTPS 필수, iOS Safari/Android Chrome 확인.
- 인가: 수정·삭제·송출 시작/종료는 서버에서 JWT 인증 + `broadcast.owner == 현재 사용자` 확인. 목록·상세·시청은 permitAll.
- 도네이션: 결제 승인 API는 서버에서 호출, 금액·대상 검증. `paymentKey`/`orderId` 유니크 제약으로 중복 적립 방지(멱등).
- 테스트 계정: README에 공개 + "테스트 계정으로 로그인" 버튼 제공 예정.

## 작업 방식 (Kidly와 동일, 2026-11-05 확정)
- GitHub: https://github.com/codes-gy/live-platform (public, 기본 브랜치 `develop`, 제출 시 `main`)
- 이슈 등록 → 브랜치 `feature|chore|fix/<도메인>/#번호-설명` → 커밋 `feat: … (#번호)` → PR(`Closes #번호`, 템플릿 사용) → CI 통과 → develop 머지
- 이슈 #1~#14 등록 완료(라벨: feat · chore · docs · 필수 · 선택). 템플릿: `.github/ISSUE_TEMPLATE/issue.md`, `.github/pull_request_template.md`
- PR 머지: CI 통과 확인 후 머지(`--merge`, 브랜치 삭제). 사용자가 "머지해줘"라고 요청했을 때 Claude가 `gh pr merge`로 진행한다(요청 없이 자동 머지는 권한 확인에서 막힘)
- 진행 현황 대시보드: https://claude.ai/artifact/SXrrCbWLq1AHjQPVZfkhTM
  - 갱신 방법: 이슈 상태 JSON(`[{issue,title,req,area,status(완료|진행 중|대기),pr,updated}]`)을 Artifact 에셋으로 올리고 `datasets/issues`의 `source.url` · `updated`를 바꾼다

## 진행 현황
### 완료
- [x] (11-05) 프로젝트 뼈대 생성
  - backend: Spring Initializr(4.1.2, Java 25) + jjwt 0.13.0 · livekit-server 0.17.0 · springdoc 3.2.0 · Testcontainers. `application.yaml`(env 기반), `contextLoads` 테스트 통과
  - frontend: create-next-app(Next 16.4.5) + LiveKit SDK, `npm run build` 통과
  - 루트: `docker-compose.yml`(로컬 PostgreSQL), `.env.example`, `.gitignore`, `CLAUDE.md`, `README.md` 초안, git init(main)

- [x] (11-05) #10 CI: `.github/workflows/ci.yml`(백엔드 `./gradlew test` + 프론트 lint · build). PR #15 develop 머지 완료
- [x] (11-05) #1 인증 백엔드: PR #16 CI 통과 후 develop 머지 완료
  - `users`(V1), BCrypt, JWT를 HttpOnly · SameSite=Lax 쿠키 `access_token`으로 발급(+ Bearer 헤더 허용), CSRF 비활성(쿠키 SameSite로 방어)
  - API: `POST /api/auth/signup|login|test-login|logout`, `GET /api/auth/me`
  - 테스트 계정 `streamer@test.com`(STREAMER) · `viewer@test.com`(VIEWER), 비밀번호 `test1234!`, 시작 시 없으면 생성(`TEST_ACCOUNTS_ENABLED`)
  - 오류 형식 `{code, message}` 통일(`ErrorCode` · `GlobalExceptionHandler` · `SecurityErrorHandlers`)
  - 공개 경로: 인증 POST 4개, GET `/api/broadcasts/**`, `/actuator/health`, Swagger. 나머지는 인증 필요
  - 테스트 14개 통과(`AuthControllerTest` 13 + contextLoads). 공통 어노테이션 `@IntegrationTest`(Testcontainers + MockMvc)
- [x] (11-05) PR #15 · #16 develop 머지, 이슈 #1 · #10 닫힘
- [x] (11-05) #2 방송 관리 API: 브랜치 `feature/broadcast/#2-broadcast-crud`, PR #17 develop 머지 완료
  - `broadcasts`(V2): owner_id FK, title(100) · description(1000, 빈 값은 null) · status(CHECK READY/LIVE/ENDED) · started_at · ended_at · created_at · updated_at, 인덱스 (status, created_at desc) · (owner_id, created_at desc)
  - API: `POST /api/broadcasts`(로그인, 201 + Location), `GET /api/broadcasts?status=&ownerId=&page=&size=`(공개, 최신순, size 최대 50, `PageResponse{items,page,size,totalElements,totalPages,hasNext}`), `GET /api/broadcasts/{id}`(공개), `PATCH`/`DELETE /api/broadcasts/{id}`(소유자만)
  - 오류 코드: `BROADCAST_NOT_FOUND`(404) · `NOT_BROADCAST_OWNER`(403) · `BROADCAST_LIVE_CANNOT_DELETE`(409) · `BROADCAST_ALREADY_LIVE` · `BROADCAST_NOT_LIVE` · `BROADCAST_ENDED`(409)
  - 엔티티 상태 전이 `start()`(READY→LIVE) · `end()`(LIVE→ENDED) · `checkDeletable()` 준비 완료 → #3에서 사용. 소유자 확인 공통 관문 `BroadcastService.findOwned(userId, id)`
  - 목록 · 상세는 `@EntityGraph(owner)`로 N+1 없음. 파라미터 형식 오류(`status=FOO`) → 400
  - `UserDetailsServiceAutoConfiguration` 제외(생성 비밀번호 로그 제거)
  - 테스트 37개 통과: `BroadcastControllerTest` 17(비로그인 · 소유자 · 다른 사용자별 등록/수정/삭제, 404, 검증, 필터 · 정렬 · 페이지 크기) + `BroadcastTest` 6(상태 전이) + 인증 14. 테스트 도우미 `AuthSupport.signupNewUser`

- [x] (11-05) #3 LiveKit 연동: 브랜치 `feature/live/#3-livekit`, PR #18 develop 머지 완료
  - API(모두 `POST /api/broadcasts/{id}/…`): `start`(소유자, READY→LIVE + 송출 토큰), `publisher-token`(소유자, LIVE, 재접속용), `end`(소유자, LIVE→ENDED + 방 닫기, ENDED면 그대로 200), `viewer-token`(공개, LIVE만, 로그인 시 닉네임). 응답 `LiveAccessResponse{serverUrl, roomName, identity, token, broadcast}`
  - 웹훅 `POST /api/livekit/webhook`(공개, LiveKit 서명 JWT + 본문 SHA-256 검증, 실패 401 `INVALID_WEBHOOK`)
    - `room_finished` → LIVE면 ENDED(아무도 안 들어옴 · 모두 나감)
    - `participant_left`(방송자) → `broadcaster-grace`(30s) 뒤 LiveKit에 방송자가 없으면 ENDED + 방 닫기(시청자가 남아 방이 안 닫히는 경우 대비, 새로고침은 유예로 보호). 예약은 메모리(`liveTaskScheduler`)라 서버 재시작 시 사라짐 → room_finished가 최후 정리
  - 패키지 `livekit/`: `RoomNames`(방 `broadcast-{id}`, 방송자 `broadcaster-{userId}`, 시청자 `viewer-{uuid}`), `LiveKitTokenIssuer`(송출: camera · microphone · screen_share · screen_share_audio / 시청: 받기 전용 · hidden), `LiveKitRoomClient`(인터페이스) + `SdkLiveKitRoomClient`(createRoom emptyTimeout 2m · departureTimeout 1m, deleteRoom 404 무시, wss→https 변환, 키 없으면 503 `LIVEKIT_NOT_CONFIGURED`), `LiveKitWebhookVerifier`
  - 동시성: `findForUpdateById`(비관적 잠금)로 상태 변경. LiveKit 호출은 트랜잭션 밖(시작=방 만들기 성공 후 LIVE, 실패 시 502 `LIVEKIT_UNAVAILABLE`이고 READY 유지 / 종료=ENDED 후 방 닫기, 실패해도 종료 유지)
  - 설정 `app.livekit.*`: url · api-key · api-secret · empty-timeout · departure-timeout · broadcaster-grace · token-ttl(1h, SDK는 밀리초로 받음 — 테스트로 확인)
  - SDK 메모: livekit-server 0.17.0은 Jackson 2(com.fasterxml) · java-jwt를 끌고 옴. Boot 4의 Jackson 3과 패키지가 달라 충돌 없음. 토큰에 `nbf` 클레임 없음
  - 테스트 70개 통과: `BroadcastLiveControllerTest` 21(시작 · 재발급 · 시청 · 종료 × 비로그인/소유자/타인, 토큰 권한 클레임, LiveKit 실패), `LiveKitWebhookControllerTest` 9(서명 위조 · 본문 변조 · 서명 없음 · 중복 · 방송자 이탈/복귀 · 시청자 이탈 · 남의 방), `LiveKitNamingTest` 3. 테스트는 `FakeLiveKitRoomClient`(@Primary)와 가짜 키(`IntegrationTest` properties)를 쓴다

- [x] (11-05) #4 프론트 로그인 · 회원가입 · 인증 상태: 브랜치 `feature/auth/#4-auth-front`, PR(머지 대기)
  - **Next.js 16 주의**: `middleware` → `proxy.ts`(`export function proxy`), `searchParams`는 Promise, `cacheComponents: true`라 `searchParams` 읽는 부분은 `<Suspense>` 안에서(밖이면 빌드 에러 "uncached data during prerendering"). `next.config.ts`의 `turbopack.rules["*.css"] = @tailwindcss/turbopack` 로더를 지우면 Tailwind `@theme`가 안 먹는다(덮어쓰지 말 것). 전역 타입 `PageProps<"/route">` · `LayoutProps<"/">` 사용
  - `/api/:path*` → `BACKEND_URL`(기본 http://localhost:8080) rewrites. 빌드 시점에 읽힘. Set-Cookie가 그대로 전달됨을 실제 서버로 확인
  - `src/lib/api.ts`(`api<T>()`, `ApiError{status, code, message}`), `src/lib/navigation.ts`(`safeNext`: `/`로 시작 · `//` · `/\` 거부, `loginPath`)
  - `components/auth/AuthProvider`(useAuth: user · status loading|authenticated|anonymous · login · signup · testLogin · logout · refresh), `RequireAuth`(만료 토큰 → 로그인으로), `LoginForm`(테스트 계정 STREAMER · VIEWER 버튼), `SignupForm`(비밀번호 확인)
  - `src/proxy.ts`: `/my/*` · `/studio/*` · `/broadcasts/new` · `/broadcasts/:id/edit`에 `access_token` 쿠키 없으면 `/login?next=` 307
  - 화면: `/`(임시 소개, #5에서 목록), `/login`, `/signup`, `/my`(계정 정보, #6에서 내 방송), 헤더. 브랜드명 "온에어", 어두운 단일 테마(강조색 ON AIR 빨강), 시스템 한글 글꼴 스택
  - 확인: lint · build 통과. 로컬 실서버(Postgres + Spring + `next start -p 3100`)로 curl 확인 — 비로그인 me 401, /my 307, 테스트 로그인 Set-Cookie(HttpOnly · SameSite=Lax), me 200, 로그아웃 Max-Age=0 후 401, 검증 메시지, `next=//evil.com` → `/`
  - 브라우저 화면 확인은 못 함(Chrome 확장 미연결)
  - `.env.example`: `NEXT_PUBLIC_API_BASE_URL` 삭제 → `BACKEND_URL`
  - 로컬 메모: 3000 포트는 사용자의 다른 Node 프로세스(포트폴리오 dev 서버로 추정)가 쓰고 있어 3100 사용

## 현재 상태
- 백엔드 필수 기능 + 프론트 인증 완료(#4 PR 머지 대기).
- 실제 LiveKit Cloud로는 아직 송출해 보지 않음(키 없음).
- 대시보드: #1 · #2 · #3 · #10 완료, #4 진행 중. "백엔드 배포 후보" 표(데이터셋 `hosting`, "Sample" 표시 = Claude 정리값)

## 다음 작업 (순서대로)
1. **#5 방송 목록 · 상세 · 시청 화면** (브랜치 `feature/broadcast/#5-…`): `/`에 방송 목록(방송 중 / 종료 탭, 페이지), `/broadcasts/[id]` 상세 + LiveKit 시청(`viewer-token` → `@livekit/components-react`로 방 접속, 방송자 트랙 재생, 자동재생 막히면 소리 켜기 버튼, 종료 시 안내). 시청 화면은 키 없으면 503 안내
2. #6 등록 · 수정 · 삭제 화면(`/broadcasts/new`, `/broadcasts/[id]/edit`, `/my`에 내 방송), #7 PC 송출, #8 모바일 송출(실제 확인에 LiveKit 키 필요)
3. **배포(#9)** — 호스팅 결정 대기
4. 선택(#11~#13): 댓글 → 도네이션 → 녹화/다시보기
5. README 완성(#14)

## 사용자 확인 필요
- LiveKit Cloud 가입 · API 키 · 토스페이먼츠 테스트 키
- 백엔드 호스팅(EC2 대신, 오라클 · GCP 제외): 11-05 후보 제시 — Railway(추천: Spring + PostgreSQL 한 곳, HTTPS 자동, 슬립 없음, 월 약 $5) · Fly.io(도쿄 리전) · Render(무료는 슬립 · 무료 DB 30일 만료) · Heroku(사용자 경험 있음) · Lightsail/DigitalOcean/Vultr VPS(기존 Docker Compose 계획 그대로) · NCP. 프론트는 Vercel + `/api` rewrites 조합. **사용자 결정 대기**
