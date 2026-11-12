# PROGRESS — live-platform (실시간 방송 플랫폼 공모전)

- 마감: **2026-11-19(목) 23:59** · 제출물: 공개 GitHub 저장소 URL 1회
- 평가 우선순위: 필수 기능 완결성 > 인증·인가 > 코드 품질·방송 상태 관리 > README > 선택 기능 가산점
- 요구사항 원문 · 항목별 진행 상태: [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) (11-12 작성, 작업 완료 시 상태 칸도 함께 갱신)
- 아키텍처 · 구조도: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) / 설계(ERD · 상태 머신 · 인가 표 · API · 시퀀스 · 결정 이유): [`docs/DESIGN.md`](docs/DESIGN.md) — 기능이 바뀌면 해당 절과 **(예정)** 표기를 함께 고친다

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

- [x] (11-05) #4 프론트 로그인 · 회원가입 · 인증 상태: 브랜치 `feature/auth/#4-auth-front`, PR #19 develop 머지 완료
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

- [x] (11-05) #5 방송 목록 · 상세 · 시청: 브랜치 `feature/broadcast/#5-watch`, PR #20 develop 머지 완료
  - `lib/broadcast.ts`: 타입(Broadcast · Page · LiveAccess), 상태 라벨, 시간 포맷(`formatAgo` · `formatDuration` · `describeTime`)
  - `/`: `BroadcastList`(탭 방송 중 · 전체 · 종료, size 12 더 보기(ID로 중복 제거), 방송 중 탭 20초 폴링), `BroadcastCard`(썸네일 대신 상태 화면 + 제목), `StatusBadge`
  - `/broadcasts/[id]`: `BroadcastDetail`(없음 · 오류 · 방송 전 10초 폴링 · 종료 화면), `LivePlayer`(viewer-token → `LiveKitRoom` audio/video=false, adaptiveStream, `useTracks([ScreenShare, Camera], onlySubscribed)` 화면 우선, `RoomAudioRenderer`(마이크 + 화면 소리, muted 토글), `StartAudio`("눌러서 소리 켜기"), 전체 화면, 연결 상태 오버레이, `DisconnectReason.ROOM_DELETED` → 종료 화면, 그 외 끊김 → 다시 연결, 키 없으면(503) 재시도 없이 안내)
  - **Next.js 16 · React 규칙 메모**: 클라이언트 컴포넌트에서 렌더 중 `Date.now()` 금지(프리렌더 에러) → 데이터 받을 때 `setNow`. eslint `react-hooks/set-state-in-effect`: 이펙트 안에서 setState 하는 함수를 직접 부르면 에러 → fetch 함수는 결과(State)를 돌려주고 `.then(setState)`로만 반영. 동적 라우트 `params`도 `<Suspense>` 안에서 await
  - 로컬 LiveKit 개발 서버로 실제 확인(`docker compose --profile livekit up -d`, devkey/secret, ws://localhost:7880): 시작 시 **실제 CreateRoom(emptyTimeout 120 · departureTimeout 60)**, 종료 시 **실제 DeleteRoom → room closed**, 다른 사용자 시작 403, 방송 전 · 종료 후 시청 토큰 409, 비로그인 시청 토큰 200, 목록 LIVE 필터, 페이지 200
  - 못 한 것: 브라우저에서 실제 영상 재생(Chrome 확장 미연결). `lk` CLI v2는 자체 토큰만 써서 우리 토큰 검증 불가 → #7에서 브라우저로 송출 · 시청 함께 확인

- [x] (11-11) #6 방송 등록 · 수정 · 삭제 화면: 브랜치 `feature/broadcast/#6-broadcast-form`, PR #21 develop 머지 완료(하위 세션이 구현, 사용자 승인 후 머지)
  - `/broadcasts/new`, `/broadcasts/[id]/edit`(소유자 아니면 403 안내 · 없으면 404 안내 · 서버 검증 메시지 표시), 화면 안 삭제 확인 `DeleteBroadcast`(방송 중 409 안내, 삭제 후 `/my`), 상세에 소유자 전용 송출 화면 · 수정 · 삭제 버튼(`OwnerActions`), `/my` 내 방송 목록 `MyBroadcasts`(`ownerId`, 20개씩 더 보기) + 방송 만들기
  - 공용: `lib/broadcast.ts`의 `loadBroadcast` · `BroadcastLoad` · `parseBroadcastId` · `TITLE_MAX` · `DESCRIPTION_MAX`, `ui.tsx`의 `Notice`, 공통 폼 `BroadcastForm`
  - **Next.js 16 주의**: 동적 경로에서 `usePathname()`을 쓰는 클라이언트 컴포넌트(RequireAuth)도 `<Suspense>` 안에 둬야 함(밖이면 빌드 에러). `cacheComponents`는 떠난 화면을 `<Activity>`로 숨겨 둠(최대 3개) → 다시 보이면 이펙트는 재실행되지만 useState(폼 입력 · 확인 패널)는 남음 → 저장 성공 폼 · 열린 확인 패널은 `useLayoutEffect` cleanup으로 초기화, 수정 폼은 `key={updatedAt}`(문서: `node_modules/next/dist/docs/01-app/02-guides/preserving-ui-state.md`)
  - 확인: lint · build, 실서버 curl(비로그인 307, 등록 201 · 400, 수정 200 · 403 · 404, 삭제 204 · 403, 방송 중 삭제 409 로컬 LiveKit). 브라우저 확인 못 함
  - 방송 중 제목 · 설명 수정은 서버가 허용하므로 화면도 허용(사용자 결정 없이 유지)
  - `/studio/[id]` 링크는 #7 전까지 404

- [x] (11-11) #11 댓글 백엔드: 브랜치 `feature/comment/#11-comment-api`, PR #22 develop 머지 완료(이슈 #11 자동 닫힘 — **댓글 프론트 UI는 아직 없음**)
  - `comments`(V3): broadcast_id FK(ON DELETE CASCADE) · author_id FK · content(500) · created_at · updated_at, 인덱스 (broadcast_id, id desc)
  - API: `POST /api/broadcasts/{id}/comments`(로그인, LIVE만, 아니면 409 `BROADCAST_NOT_LIVE`/`BROADCAST_ENDED`), `GET …/comments?before=&size=`(공개, 최신순 커서 `{items,hasNext,nextBefore}`, size 기본 50 최대 100, 종료 방송 포함), `PATCH …/comments/{cid}`(작성자), `DELETE …/comments/{cid}`(작성자 · 방송 소유자). 오류 코드 `COMMENT_NOT_FOUND` · `NOT_COMMENT_AUTHOR`. 응답 `{id, broadcastId, author{id,nickname}, content, createdAt, updatedAt}`
  - SSE `GET …/comments/stream`(공개): 이벤트 `comment` · `comment-updated` · `comment-deleted`(`{id}`) · `ended`(`{broadcastId}`, 보낸 뒤 닫힘). 커밋 뒤 전송(`@TransactionalEventListener` AFTER_COMMIT), 메모리 구독 목록(`CommentStreams`) · 30분 타임아웃 · 20초 `:ping` · `retry:3000` · `X-Accel-Buffering: no`. 이미 종료된 방송 구독 시 즉시 `ended`
  - 종료는 `BroadcastLiveService.endLocked()` 한 곳에서 `BroadcastEndedEvent` 발행(소유자 종료 · 웹훅). 댓글 작성은 방송 행 `FOR SHARE` 잠금 → 종료 커밋 뒤 댓글 생성 불가. `Broadcast.checkLive()`로 LIVE 확인 통합. `GlobalExceptionHandler` 오류 응답 JSON 고정(SSE 요청 404가 406/예외로 바뀌던 문제)
  - 테스트 97개 통과(`CommentControllerTest` 27 추가). 실서버 curl로 SSE 헤더 · 이벤트 순서 확인
  - 후속: 레이트 리밋, 재연결 때 프론트가 목록 첫 페이지를 다시 받아 id로 합치기, Next rewrite 경유 SSE 버퍼링(gzip) 확인 → 필요하면 `compress: false`, 다중 서버면 pub/sub

- [x] (11-12) #7 PC 송출: 브랜치 `feature/live/#7-pc-broadcast`(하위 세션 구현 · 메인 세션 검토), PR #24 develop 머지 완료(11-12)
  - `/studio/[id]`(RequireAuth + 소유자 아니면 "송출할 권한이 없습니다", getDisplayMedia 없는 브라우저는 PC Chrome/Edge 안내)
  - `components/studio/`: `media.ts`(`Capture{screen, screenAudio, mic, micMuted}`, `captureScreen` = `createLocalScreenTracks({audio, systemAudio:"include", selfBrowserSurface:"exclude", surfaceSwitching:"include", 1080p30})`, 취소는 null, `captureMic` 실패 null), `useBroadcastSession.ts`(상태 `setup | connecting | live | ending | ended`, raw `Room`으로 직접 연결 · 퍼블리시), `Studio.tsx`(화면/마이크 선택 → 미리보기 → 방송 시작 · 재개, 마이크 켜기/끄기(방송 전) · 음소거(방송 중), 화면 바꾸기, 종료 확인, 경과 시간, beforeunload 경고)
  - setup은 READY면 "방송 시작"(`start`), LIVE면 "방송 재개"(`publisher-token`). ROOM_DELETED · `BROADCAST_ENDED` → 종료 화면. 그 밖의 끊김 · 연결 실패 → setup + 안내 문구(재개 버튼으로 다시 연결)
  - 브라우저 "공유 중지" 막대 → 방 연결은 유지, 화면만 비우고 다시 선택 안내(SDK가 끝난 ScreenShare 트랙을 자동 unpublish — `handleTrackEnded` 확인)
  - **결정**: 화면을 떠나거나 `<Activity>`로 숨겨지면 방 연결 · 캡처를 모두 정리(숨은 탭에서 몰래 화면을 캡처하지 않도록). 종료 API는 부르지 않음 → 30초 안에 돌아와 "재개"하면 이어지고, 아니면 서버 유예로 자동 종료. 오래된 비동기 결과는 epoch 카운터로 무시
  - 확인: lint · build 통과. **실제 LiveKit Cloud로 Playwright E2E 통과**(Chromium 가짜 화면 · 마이크): 비로그인 /studio → 로그인 이동, 화면 선택 전 시작 비활성, 미리보기, 시작 → 서버 LIVE, 비로그인 시청자가 영상(videoWidth>0) + 오디오 2개(마이크 · 화면 소리) 수신, 음소거 토글, 새로고침 → "방송 재개" · 서버 LIVE 유지 → 재개 후 시청자 영상 복귀, 종료 → 양쪽 "방송이 종료되었습니다" · 서버 ENDED · 캡처 정리, 다른 사용자 /studio 권한 없음 안내 · start API 403
  - 스크립트: 세션 scratchpad의 `e2e/pc-broadcast.mjs`(저장소 밖). 커밋하려면 `frontend/e2e/`로 옮기고 Playwright 의존성 추가 필요
  - 못 한 것: 실제 Chrome 탭 오디오 · Windows 시스템 오디오 체크박스 동작, 다른 기기(휴대폰)에서 시청 → 사용자 수동 확인 필요
  - 알려진 빈틈: start 요청 중 화면을 떠나면 서버는 LIVE인데 방송자 없음 → LiveKit 방 emptyTimeout(2분)으로 room_finished → ENDED 정리

- [x] (11-12) #8 모바일 송출: 브랜치 `feature/live/#8-mobile-broadcast`(#7 브랜치 b95a0ae 위, 워크트리 `../live-platform-8`), 커밋 2개 · **push · PR 전**(사용자 승인 대기)
  - 하위 세션(poteto-agent, sonnet)이 구현, 메인 세션이 검토 · 수정 · E2E
  - 모양: `StudioMode = "screen" | "camera"`, `Capture{video?: VideoCapture, mic?, micMuted}`, `VideoCapture = {kind:"screen", track, audio?} | {kind:"camera", track, facing}`. `screen`/`screenAudio` 필드는 없앰
  - 휴대폰(`getDisplayMedia` 없음)은 카메라만, PC는 setup에서 "화면 공유 | 카메라" 선택(웹캠 송출 가능). 둘 다 없으면 HTTPS · 지원 브라우저 안내. 카메라 켤 때 마이크도 요청, 전면 카메라 미리보기만 거울 반전, 전면/후면 전환은 `restartTrack({facingMode})`(같은 퍼블리케이션 유지, 휴대폰에서만 버튼), Wake Lock(`useWakeLock`), 미리보기 상자는 카메라 모드 좁은 화면에서 3:4
  - **검토에서 고친 것**: 하위 세션은 방송 중 카메라 Ended 때 트랙을 직접 내렸는데, LiveKit SDK가 SDK가 만든 카메라 트랙은 Ended 시 자동으로 다시 잡고(실패 시 음소거) 모바일 앱 복귀 때도 재획득한다(`livekit-client.esm.mjs` `handleTrackEnded` · `handleAppVisibilityChanged`) → 방송 중에는 SDK에 맡기고 setup에서만 미리보기를 비우도록 수정
  - 확인: lint · build(Turbopack) 통과. **로컬 LiveKit(dev) + Playwright E2E 34개 통과**(scratchpad `e2e/mobile-broadcast.mjs`, Chrome for Testing 1248 + 가짜 카메라/마이크): 휴대폰 에뮬레이션(Pixel 7, getDisplayMedia 제거) 모드 선택 없음 · 카메라 전 시작 비활성 · 미리보기 거울 · 마이크 함께 잡힘 · 시작 → LIVE · 비로그인 시청자 영상 + 오디오 수신 · 방송 중 전환 후 미리보기 반전 해제 · 시청자 영상 유지 · 종료 → 양쪽 종료 화면 · ENDED · 미리보기 해제 / PC 웹캠 모드 같은 흐름(전환 버튼 숨김) / PC 화면 공유 회귀(시청자 1920x1080, 종료). iPhone SE 폭에서 가로 넘침 0px 스크린샷 확인
  - 못 한 것: **실제 휴대폰**(iOS Safari · Android Chrome) 송출 · 후면 카메라 · 앱 전환 복구 · Wake Lock — HTTPS 필요 → 배포(#9) 후 또는 터널로 사용자 확인
  - 알려진 점: 시청 플레이어는 16:9라 세로 카메라는 좌우 여백(letterbox). 카메라 모드에 "카메라 끄기" 버튼 없음(모드 전환 · 화면 이탈로 해제). 재획득 실패로 음소거된 경우 방송자 화면에 별도 안내 없음

- [x] (11-12) PR #24(#7) · PR #25(#8 + 문서) develop 머지, 이슈 #8 닫힘
- [x] (11-12) #11 댓글 프론트: 브랜치 `feature/comment/#11-comment-ui`, PR #26 develop 머지 완료(하위 세션 구현 · 메인 세션 검토 · E2E)
  - `lib/comments.ts`(타입 · id 키 리듀서 · `fetchComments` · `formatCommentTime`), `components/comment/`(`useComments` 목록 + SSE, `CommentPanel` 자동 스크롤 · "새 댓글" 버튼 · 이전 댓글 더 보기, `CommentComposer` Enter 전송 · Shift+Enter 줄바꿈 · IME 조합 중 무시 · 500자, `CommentItem` 인라인 수정 · 삭제 확인 · 방송자 배지 · (수정됨))
  - 상세 화면: lg 이상 2단(왼쪽 플레이어 · 정보, 오른쪽 댓글), 모바일은 아래. 컨테이너 `max-w-6xl`
  - 상태별: LIVE 작성 가능(비로그인은 로그인 링크), READY 안내, ENDED 읽기 전용. 수정 · 삭제는 서버가 상태와 무관하게 허용하므로 화면도 허용
  - **`next.config.ts` `compress: false`**: Next 기본 gzip이 SSE를 모아 둬서 실시간 수신이 멈춤(하위 세션이 가짜 SSE 서버로 재현). 배포 시 압축은 호스팅/프록시가 맡음
  - 확인: lint · build. **Playwright E2E 22개 통과**(scratchpad `e2e/comments.mjs`, 실제 Spring + Next rewrite): READY 안내, 비로그인 로그인 링크, 작성 → 비로그인 화면에 SSE로 250ms 도착, 내 댓글 중복 없음, 방송자 배지, 권한별 버튼(비로그인 없음 · 작성자 수정/삭제 · 방송 소유자는 남의 댓글 삭제만), 남의 댓글 수정 API 403, 수정 실시간 반영 (수정됨), 소유자 삭제 실시간 제거, 새로고침 후 유지, 종료 → 읽기 전용, 375px 가로 넘침 0
  - 못 한 것: 실제 한글 IME 입력, 네트워크 끊김 후 재연결 보충, 이전 댓글 불러올 때 스크롤 위치

- [x] (11-12) #28 화면 다듬기(디자이너 관점 검토 반영): 브랜치 `feature/ui/#28-ui-polish`, PR #29 develop 머지 완료(11-12, 이슈 #28 닫힘)
  - 검토: PC 1440px · 휴대폰 390px로 전 화면 스크린샷(scratchpad `e2e/review.mjs`) → 문제 목록을 사용자에게 보고 후 "개선해줘"로 진행
  - 글꼴: CSS에 이름만 있던 Pretendard를 실제 로드(`layout.tsx`의 `<link precedence>`, jsDelivr 동적 서브셋 v1.3.9, OFL-1.1 → README 출처 표기 필요). 본문 `word-break: keep-all` · 제목 `text-wrap: balance`
  - 목록 카드: 썸네일 안 제목 중복 제거 → 방송자 이니셜(`OwnerAvatar`) + "N분째 방송 중"/"방금 시작"/"N분 방송"/"방송 예정". 아래 줄은 닉네임(+ 종료 · 등록 시각)
  - `formatDuration` 1분 미만은 "1분 미만". 종료 화면 "○○님이 1분 미만 동안 방송했습니다" + 이니셜
  - 스튜디오: 화면 모양 스켈레톤(`RequireAuth fallback`), **댓글 패널**(lg 2단, 방송자가 실시간 확인 · 삭제), **마이크 음량 막대**(WebAudio AnalyserNode, transform 직접 갱신), 시작 비활성 이유 안내, 안내 화면은 좁게(`Narrow`)
  - **시청자 수**: hidden 시청자도 `room.numParticipants`에 포함됨을 로컬 LiveKit으로 확인(방송자 1 + 시청자 2 → 3) → `numParticipants - 1`을 3초마다 읽음(`useViewerCount`). 상세 플레이어 LIVE 옆 · 스튜디오 제목 옆. 서버 변경 없음. 스튜디오 phase(live · ending)에 `room` 추가
  - 비활성 버튼(PrimaryButton · 댓글 등록 · 수정 저장)을 흐린 빨강 → 회색. 휴대폰 댓글 패널 높이 32rem → 24rem(sm 28rem)
  - 확인: lint · tsc · build 통과. 회귀 E2E 댓글 22개 · 송출 34개 통과. 새 E2E 15개 통과(scratchpad `e2e/ui-polish.mjs`): 글꼴 로드 · keep-all, 시작 비활성 안내, 스튜디오 댓글 패널, 음량 막대 움직임, 시청자 수 스튜디오 · 플레이어 2명 → 1명 나가면 1명, 시청자 댓글이 스튜디오에 실시간 도착 · 방송자 삭제 → 시청자 화면에서 제거, 카드 제목 1회 · 캡션, 종료 "1분 미만", 휴대폰 가로 넘침 0. E2E가 카드 시간 중복("방금 시작" 2번)을 잡아 수정
  - 문서: `DESIGN.md` 6.2 · 9절, `ARCHITECTURE.md` 프론트 트리, `REQUIREMENTS.md` #28 출처

- [x] (11-12) #9 배포(Heroku): 브랜치 `chore/deploy/#9-heroku`, PR 생성 · 머지 대기
  - 사용자 결정: GitHub 학생 크레딧(Heroku $312 / 24개월, 잔액 $298.25, 2028-08-31 만료)으로 Heroku에 전부 배포. 기존 앱 `mundansok` · `welive-backend` 삭제(사용자 확인, config vars 포함). **운영 시간 09~20시 KST**(사용자 선택, 접속 불가 감점 위험은 고지함)
  - 앱: `onair-live-web`(https://onair-live-web-28fc2f0d1715.herokuapp.com) · `onair-live-api`(https://onair-live-api-daf462a2a9af.herokuapp.com), 컨테이너 스택 · US · Basic 다이노, `heroku-postgresql:essential-0`(PostgreSQL, 최대 $5/월). 예상 약 $11/월(다이노 11시간/일)
  - 파일: `backend/Dockerfile`(temurin 25 jdk 빌드 → jre 실행) · `backend/docker-entrypoint.sh`(DATABASE_URL → JDBC, sslmode=require) · `frontend/Dockerfile`(node 24, standalone, `BACKEND_URL` 빌드 인자) · `scripts/deploy-heroku.sh [api|web|all]` · `.github/workflows/dyno-schedule.yml`(cron 0 0 / 0 11 UTC, 수동 on/off, 시크릿 `HEROKU_API_KEY` = write 범위 전용 authorization). `application.yaml` `server.port: ${PORT:8080}`, `next.config.ts` `output: "standalone"`
  - 시행착오: Heroku 레지스트리가 buildx OCI 매니페스트 · 출처 증명을 `unsupported`로 거절 → `--output type=image,push=true,oci-mediatypes=false` + `--provenance=false`. 힙 70%로 R14(메모리 초과) → 힙 45% · SerialGC · 코드 캐시 48m · 메타스페이스 160m · C1 · Tomcat 스레드 40으로 기동 8초, R14 없음
  - 백엔드 config: JWT_SECRET(무작위, 출력 안 함) · COOKIE_SECURE=true · CORS_ALLOWED_ORIGINS=프론트 주소 · TEST_ACCOUNTS_ENABLED=true · JAVA_TOOL_OPTIONS · SERVER_TOMCAT_THREADS_MAX. **LIVEKIT_* 미설정**
  - 확인: Flyway V1~V3 적용, health 200, 프론트 `/` · `/login` · `/api/broadcasts` 200, 테스트 로그인 쿠키 `Secure; HttpOnly; SameSite=Lax`, 방송 등록 · 삭제, **댓글 SSE가 Heroku + Next rewrite에서 버퍼링 없음**(즉시 `:connected`, 20초 `:ping`), start는 LiveKit 키 없어 503(예상대로)

- [x] (11-12) #32 서비스 이름 "핏" · 토끼 파비콘 · 도메인: 브랜치 `feature/ui/#32-brand-pit`(PR #31 위에 쌓음), PR 생성 · 머지 대기. **배포 서버에는 이미 반영**
  - 이름 후보를 여러 번 제안 · 겹침 검색(Cue는 해외 방송 서비스와 겹침, Tally는 설문 폼 서비스) 후 사용자가 "핏"으로 결정. 로고 · 탭 제목(`%s · 핏`) · README · 문서 제목 변경. Heroku 앱 이름(onair-live-*)은 유지
  - 파비콘: `app/favicon.ico` 삭제 → `app/icon.svg`(흰 토끼 + 분홍 귀 · 볼 + 오른쪽 위 빨간 LIVE 점). 배포 HTML에 `<link rel="icon" href="/icon.svg…">` 확인
  - 도메인: `heroku domains:add pit-live.kro.kr -a onair-live-web` → DNS 대상 `adjacent-rook-ze64mynua57li9kw25hozhw3.herokudns.com`(CNAME), ACM(자동 인증서) 켬. **사용자가 내도메인.한국에서 CNAME 등록 필요**(11-12 기준 아직 해석 안 됨). 백엔드 CORS에 새 주소 추가
  - 로고: 깜빡이는 빨간 점 → 토끼 실루엣이 아래에서 빨갛게 차올랐다 비워지는 애니메이션(`Logo.tsx` clipPath + `logo-rabbit-fill` keyframes, 동작 줄이기 설정이면 멈춤). clipPath 안에 `<g>`를 넣으면 잘라내기 영역이 비어 채움이 안 보였음 → 도형을 직접 넣음
  - **밝은 테마**(사용자 "왜 화면이 어둡지?"): 색 토큰만 바꿔 전 화면 전환(바탕 #f5f6f8 · 카드 흰색 · 글자 #15171c, 영상 자리만 검정), 메인 소개 카드(특징 칩 · 방송 시작하기 · 둘러보기), 카드 그림자 · 호버, 푸터. 배포 서버 반영
  - 이름 최종 **"지이잉"**(11-12, 기계가 켜지는 소리 · 치지직 같은 의성어 계열). "핏"에서 변경: `layout.tsx` · `Logo.tsx` · `Footer.tsx` · `icon.svg` 주석 · README · 문서 제목. 도메인 `pit-live.kro.kr`은 이름과 안 맞음 → 사용자에게 새 도메인 여부 확인
  - 백엔드용 `api.pit-live.kro.kr`은 필수 아님: 브라우저는 프론트의 `/api`만 부르고, 백엔드 주소가 필요한 곳은 LiveKit 웹훅뿐(herokuapp 주소로 충분)

## 현재 상태
- (11-12) **배포됨(Heroku, 09~20시 KST)**: https://onair-live-web-28fc2f0d1715.herokuapp.com — LiveKit 키 · 웹훅 등록 전이라 송출 불가. 운영 시간 예약은 PR 머지 후(기본 브랜치에서만 cron 동작)
- (11-12) 필수 기능 코드 전부 develop 반영(#1~#8). 선택 댓글(#11) 백엔드 + 프론트 완료. 화면 다듬기 #28 머지 완료(PR #29). 남은 것: 배포(#9, 호스팅 결정 대기) · README(#14) · 시연 영상 · 실기기 확인 · 선택 #12 도네이션 · #13 녹화
- (11-12) 비용: 사용자가 LiveKit 비용을 문의 → Cloud 무료 Build 플랜(카드 불필요, 월 참가자 5,000분 · 내려받기 50GB · 동시 100명 · 녹화 60분, 초과 시 과금 대신 차단, 계정 단위)으로 충분하다고 답함. 코드 변경 없이 유지
- (11-12) 로컬 macOS에는 루트 `.env`(LiveKit Cloud 키)가 없음 → `docker compose --profile livekit up -d`(devkey/secret, ws://localhost:7880). 백엔드 실행: `JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-25.0.4.1/Contents/Home LIVEKIT_URL=ws://localhost:7880 LIVEKIT_API_KEY=devkey LIVEKIT_API_SECRET=secret ./gradlew bootRun`. 프론트 `npm run dev`(3000) · E2E는 `next start -p 3100`. 워크트리에서 `node_modules`를 심볼릭 링크하면 Turbopack 빌드 실패 → `npm ci`
- (11-12) 로컬 DB에 11-05의 "CLI 송출 시험"(id 2)이 LIVE로 남아 있음(로컬은 웹훅 미연결이라 종료 신호를 못 받음). 운영에서도 웹훅 유실 시 같은 일이 생길 수 있어 정리 작업을 다음 작업에 넣음
- (11-12) 문서: `docs/REQUIREMENTS.md`(요구사항 · 상태) · `docs/ARCHITECTURE.md` · `docs/DESIGN.md`. 미구현 부분(배포 #9 · 도네이션 #12 · 녹화 #13)은 **(예정)** 설계
- 작업 방식(11-11): 메인 세션이 하위 세션(pstack:poteto-agent, worktree 격리)에 나눠 맡기고, 결과를 모아 사용자 승인 후 push · PR · 머지. 하위 세션은 로컬 커밋까지만, PROGRESS.md는 메인 세션만 수정
- 대시보드: #1~#8 · #10 · #11 완료로 갱신 필요(미갱신)
- 다른 개발 PC(WSL2, `/mnt/a/project/live-platform`): Java 25 `/usr/lib/jvm/java-25-openjdk-amd64`, 루트 `.env`에 LiveKit Cloud 키 있음 — `set -a; . ../.env; set +a` 후 bootRun
- E2E: scratchpad `e2e/`(저장소 밖) — `pc-broadcast.mjs` · `mobile-broadcast.mjs` · `comments.mjs` · `ui-polish.mjs` · `review.mjs`(스크린샷). Postgres + 백엔드(8080) + `next start -p 3100` 후 `node <파일>`

## 다음 작업 (순서대로)
1. **배포 마무리(#9)** — ① 사용자가 LiveKit Cloud 키를 `heroku config:set -a onair-live-api LIVEKIT_URL=… LIVEKIT_API_KEY=… LIVEKIT_API_SECRET=…`로 직접 입력(채팅에 키를 붙이지 않음) ② LiveKit Cloud 웹훅 URL `https://onair-live-api-daf462a2a9af.herokuapp.com/api/livekit/webhook` 등록 ③ 배포 PR 머지 → `dyno-schedule` 수동 실행으로 토큰 확인 ④ 운영 주소에서 PC · 휴대폰 송출 E2E. 그다음 실제 휴대폰으로 #8 확인(송출 · 후면 전환 · 앱 전환 복구 · 다른 기기 시청), PC Chrome 탭 오디오, 댓글 SSE가 호스팅 프록시에서 버퍼링되지 않는지 확인. LiveKit Cloud 웹훅 URL 등록
2. README(#14) + 시연 영상(PC · 모바일 송출 · 시청 · 종료). 출처에 LiveKit · Pretendard(OFL-1.1) 포함
2-1. 화면 검토 후속(여유 있을 때): 휴대폰 헤더에 "방송 목록 · 내 방송" 메뉴(지금은 sm 미만에서 숨김), 상세에 "휴대폰으로 보기" QR · 링크 복사(시연용), 로그인 화면 테스트 계정 버튼 강조, **LIVE로 남은 방송 정리**(주기적으로 LiveKit에 방 · 방송자 존재 확인 → 없으면 ENDED, 웹훅 유실 대비 · 상태 관리 평가 항목)
3. 선택 #12 도네이션(토스페이먼츠 테스트 키 필요) · #13 녹화(LiveKit Egress + S3 호환 스토리지 필요)

## 사용자 확인 필요
- (11-05, 11-11 설치 확인됨) **pstack 플러그인** 사용 요청: `michael-denyer/pstack-claude`(MIT, v0.9.129, Cursor pstack의 Claude Code 이식). `/plugin` 명령은 Claude가 실행할 수 없어 사용자가 직접 설치 → 새 세션부터 `/poteto-mode`로 작업 라우팅. SessionStart · PreToolUse 훅이 모든 프로젝트에 적용됨. 설치되면 #6부터 pstack 흐름(poteto-mode)으로 진행하되 이 문서의 4단계 규칙 · 이슈/PR 흐름은 유지
- 방송 중 제목 · 설명 수정 허용 여부(현재 허용)
- ~~LiveKit Cloud 키~~ 11-12 완료. 토스페이먼츠 테스트 키는 #12 때
- ~~백엔드 호스팅~~ 11-12 Heroku로 결정 · 배포. (이전 기록) 11-05 후보 제시 — Railway(추천: Spring + PostgreSQL 한 곳, HTTPS 자동, 슬립 없음, 월 약 $5) · Fly.io(도쿄 리전) · Render(무료는 슬립 · 무료 DB 30일 만료) · Heroku(사용자 경험 있음) · Lightsail/DigitalOcean/Vultr VPS(기존 Docker Compose 계획 그대로) · NCP. 프론트는 Vercel + `/api` rewrites 조합. **사용자 결정 대기**
