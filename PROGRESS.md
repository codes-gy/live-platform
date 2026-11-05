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

## 진행 현황
### 완료
- [x] (11-05) 프로젝트 뼈대 생성
  - backend: Spring Initializr(4.1.2, Java 25) + jjwt 0.13.0 · livekit-server 0.17.0 · springdoc 3.2.0 · Testcontainers. `application.yaml`(env 기반), `contextLoads` 테스트 통과
  - frontend: create-next-app(Next 16.4.5) + LiveKit SDK, `npm run build` 통과
  - 루트: `docker-compose.yml`(로컬 PostgreSQL), `.env.example`, `.gitignore`, `CLAUDE.md`, `README.md` 초안, git init(main)

## 현재 상태
- 뼈대만 존재. 도메인/엔티티/마이그레이션 없음. Security는 기본 설정(전부 인증 필요) 상태.
- 아직 커밋 전, GitHub 원격 저장소 미생성.

## 다음 작업 (순서대로)
1. **인증**: `users` 마이그레이션, 회원가입/로그인/로그아웃(JWT, HttpOnly 쿠키 또는 Bearer 결정), SecurityConfig(permitAll 경로·CORS), `/api/auth/me`, 테스트 계정 시드 + 인가 테스트
2. **방송 관리**: `broadcasts` 엔티티(제목·설명·상태·소유자·시작/종료 시각), CRUD API + 소유자 권한 검증 + 비로그인/소유자/타인 테스트
3. **LiveKit 연동**: 송출 토큰(소유자)/시청 토큰(누구나) 발급, 방송 시작·종료 API(종료 시 deleteRoom), 웹훅 수신
4. **프론트**: 로그인/회원가입, 방송 목록·상세·시청, 방송 등록·수정, PC 송출(화면+탭/시스템 소리+마이크), 모바일 송출(카메라+마이크)
5. **배포**: EC2 + Docker Compose + Nginx + HTTPS, LiveKit Cloud 프로젝트·키 발급 → 실기기(PC→폰, 폰→PC) 확인
6. 선택: 댓글 → 도네이션(토스 테스트) → 녹화/다시보기(Egress → S3)
7. README 완성(서비스 URL, 테스트 계정, 실행 방법, 기능 확인 절차, 지원 환경, 오픈소스 출처)

## 사용자 확인 필요
- GitHub 저장소 이름/공개 여부 생성 시점
- 도메인 보유 여부 (HTTPS 인증서용) · AWS 계정/S3 버킷 · LiveKit Cloud 가입 · 토스페이먼츠 테스트 키
