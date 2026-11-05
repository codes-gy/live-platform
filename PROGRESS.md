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
- [x] (11-05) #2 방송 관리 API: 브랜치 `feature/broadcast/#2-broadcast-crud`, PR 생성(CI 결과 확인 · 머지 대기)
  - `broadcasts`(V2): owner_id FK, title(100) · description(1000, 빈 값은 null) · status(CHECK READY/LIVE/ENDED) · started_at · ended_at · created_at · updated_at, 인덱스 (status, created_at desc) · (owner_id, created_at desc)
  - API: `POST /api/broadcasts`(로그인, 201 + Location), `GET /api/broadcasts?status=&ownerId=&page=&size=`(공개, 최신순, size 최대 50, `PageResponse{items,page,size,totalElements,totalPages,hasNext}`), `GET /api/broadcasts/{id}`(공개), `PATCH`/`DELETE /api/broadcasts/{id}`(소유자만)
  - 오류 코드: `BROADCAST_NOT_FOUND`(404) · `NOT_BROADCAST_OWNER`(403) · `BROADCAST_LIVE_CANNOT_DELETE`(409) · `BROADCAST_ALREADY_LIVE` · `BROADCAST_NOT_LIVE` · `BROADCAST_ENDED`(409)
  - 엔티티 상태 전이 `start()`(READY→LIVE) · `end()`(LIVE→ENDED) · `checkDeletable()` 준비 완료 → #3에서 사용. 소유자 확인 공통 관문 `BroadcastService.findOwned(userId, id)`
  - 목록 · 상세는 `@EntityGraph(owner)`로 N+1 없음. 파라미터 형식 오류(`status=FOO`) → 400
  - `UserDetailsServiceAutoConfiguration` 제외(생성 비밀번호 로그 제거)
  - 테스트 37개 통과: `BroadcastControllerTest` 17(비로그인 · 소유자 · 다른 사용자별 등록/수정/삭제, 404, 검증, 필터 · 정렬 · 페이지 크기) + `BroadcastTest` 6(상태 전이) + 인증 14. 테스트 도우미 `AuthSupport.signupNewUser`

## 현재 상태
- 백엔드 인증 · 방송 CRUD 완료. #2 PR 머지 대기(사용자가 "머지해줘" 하면 CI 확인 후 머지).
- CI가 develop 대상 PR마다 돈다(백엔드 약 1분 30초, 프론트 약 1분 10초).
- 대시보드: #1 · #10 완료, #2 진행 중(PR 연결).

## 다음 작업 (순서대로)
1. **#3 LiveKit 연동** (브랜치 `feature/live/#3-…`): 송출 토큰(소유자, 방송 시작 시 LIVE 전환)/시청 토큰(누구나, LIVE만) 발급, 방송 종료 API(ENDED + LiveKit deleteRoom), 웹훅 수신(서명 검증, room_finished로 비정상 종료 정리). LiveKit 키 없이도 테스트되게 RoomService는 인터페이스로 감싸 테스트 대역 사용
   - 필요: LiveKit Cloud 가입 · API 키(로컬 실제 송출 확인용). 테스트만이면 불필요
2. 프론트부터 갈지 #3부터 갈지는 사용자와 확인 (기본: #3 → #4)
3. **프론트(#4~#8)**: 로그인/회원가입, 방송 목록·상세·시청, 방송 등록·수정, PC 송출(화면+탭/시스템 소리+마이크), 모바일 송출(카메라+마이크)
4. **배포(#9)**: EC2 + Docker Compose + Nginx + HTTPS, LiveKit Cloud 프로젝트·키 발급 → 실기기(PC→폰, 폰→PC) 확인
5. 선택(#11~#13): 댓글 → 도네이션(토스 테스트) → 녹화/다시보기(Egress → S3)
6. README 완성(#14)(서비스 URL, 테스트 계정, 실행 방법, 기능 확인 절차, 지원 환경, 오픈소스 출처)

## 사용자 확인 필요
- 도메인 보유 여부 (HTTPS 인증서용) · AWS 계정/S3 버킷 · LiveKit Cloud 가입 · 토스페이먼츠 테스트 키
