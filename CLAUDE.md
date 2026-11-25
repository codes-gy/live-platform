# 지이잉 (live-platform)

## 작업 순서 (매 작업)
1. **읽기**: 루트 `PROGRESS.md` 전체를 읽는다. 없으면 README · docs · 실제 코드로 진행 상황을 조사해 새로 만든다.
2. **브리핑**: 현재 상태와 '다음 작업'을 확인하고, 구현 방안을 짧게 요약해 사용자에게 알린다.
3. **실행**: 코드를 고치고, 필요하면 테스트까지 돌린다.
4. **기록**: `PROGRESS.md`의 완료 항목 · 현재 상태 · 다음 작업을 갱신한다. 기능이 바뀌면 `docs/DESIGN.md` · `docs/ARCHITECTURE.md`의 해당 절도 함께 고친다.

끝나면 "PROGRESS.md 업데이트 완료. 다음 단계를 위해 터미널을 초기화해 주세요."라고 알린다.

`PROGRESS.md`가 약 500줄(25,000 토큰)을 넘으면 읽기 단계에서 압축한다: 최근 작업 · 현재 상태 · 다음 작업은 그대로, 오래된 완료 항목은 결론과 최종 결정만 남기고, 이슈 목록 · 체크리스트 · 미확정 사항은 빠뜨리지 않는다. 압축 사실과 날짜를 기록 단계에 남긴다.

## 먼저 읽을 것
- **용어** → [GLOSSARY.md](GLOSSARY.md): 화면 문구 · 오류 메시지 · 새 클래스에 이름을 붙일 때(후원/도네이션, 댓글/채팅처럼 갈리는 말).
- **결정** → [docs/adr/](docs/adr/): LiveKit 연동 · 방송 상태 · 메모리 상태(SSE · 타이머 · 요청 제한) · 후원 승인 · 운영 DB를 바꾸려 할 때.
- **설계 상세** → [docs/DESIGN.md](docs/DESIGN.md): 인가 표 · API · 시퀀스. 배포 절차는 [docs/DEPLOY.md](docs/DEPLOY.md).
- **프론트엔드** → [frontend/AGENTS.md](frontend/AGENTS.md): 코드 작성 전 `node_modules/next/dist/docs/`부터 본다(학습 데이터와 다른 Next.js 16).

## 지켜야 할 불변식
- 방송 상태 전이는 `Broadcast`의 `start()` · `end()`에만 둔다. 모든 종료 경로는 `BroadcastLiveService.endLocked()`를 지나고, 상태를 바꾸는 쪽은 `findForUpdateById`로 행을 잠근다.
- LiveKit · 토스 같은 외부 호출은 DB 트랜잭션과 행 잠금 밖에서 한다. LiveKit 방 생성이 성공해야만 방송 중으로 바꾼다.
- 소유자 확인은 `BroadcastService.findOwned` 한 곳에서 한다(없으면 404, 남의 것이면 403).
- 후원 금액 · 대상은 서버에 저장한 후원 주문만 믿는다. 결과를 모르는 승인은 `CONFIRMING`으로 남긴다(ADR 0004).
- LiveKit SDK는 `livekit/` 패키지 안에만 둔다. 방 · 참가자 이름은 `RoomNames` 규칙으로 만들고 DB에 저장하지 않는다.
- 비밀 값(.env, 키, 토큰)은 커밋하지 않는다. 예시는 `.env.example` · `.env.prod.example`에만 둔다.

## 테스트
- 백엔드 `./gradlew test`는 **JDK 25**(Gradle 툴체인)와 **실행 중인 Docker**가 필요하다(Testcontainers `postgres:18-alpine`). Docker가 없으면 통합 테스트는 CI에서 확인한다.
- LiveKit · 토스 · R2 키는 필요 없다. `@IntegrationTest`가 가짜 키와 `FakeLiveKitConfig`의 가짜 클라이언트를 넣고, 주기 작업은 1시간으로 늦춰 두므로 테스트에서 `reconcileLive()` · `reconcileConfirming()`을 직접 부른다. 새 통합 테스트도 `@IntegrationTest`를 붙여 컨텍스트를 재사용한다. 외부 연동을 새로 만들면 인터페이스 + `support/`의 Fake를 함께 만든다.
- `frontend/e2e`(Playwright)는 LiveKit 키가 있는 실행 중인 서버가 필요해 CI에서 돌지 않는다.

## 함정
- **운영은 상시 운영이다.** `.github/workflows/dyno-schedule.yml`은 파일은 있지만 GitHub Actions 설정에서 꺼 둔(`disabled_manually`) 상태다. `develop`에는 이를 다시 켜고 문서를 "운영 시간 09~20시"로 바꾼 커밋 e05ef6b(#68, PR #69)가 남아 있어 사실과 다르다. 그 문구를 근거로 운영 시간을 안내하거나 워크플로를 다시 켜지 말고, `fix/advancement-2026-11`과 합칠 때는 상시 운영 쪽으로 정리한다.
- 프론트 `BACKEND_URL`은 빌드 시점에 이미지에 들어간다. 바꾸면 다시 빌드 · 배포해야 한다.
- `next.config.ts`의 `compress: false`는 댓글 SSE가 gzip에 묶여 멈추지 않게 하려는 것이다.
- 셸 스크립트는 `.gitattributes`로 LF를 강제한다. Windows에서 CRLF로 바뀌면 컨테이너가 `docker-entrypoint.sh`를 찾지 못한다.
- `PROGRESS.md` 앞부분의 초기 결정(EC2 배포, 테스트 로그인 버튼 등)은 이후 바뀌었다. 현재 운영 구성은 `docs/DEPLOY.md`가 기준이다.

## 작업 흐름
- 기본 브랜치는 `develop`, 제출 · 릴리스는 `main`. 이슈 → `feature|fix|chore/<도메인>/#번호-설명` → 커밋 `feat: … (#번호)` → PR 제목 `[FEAT] …`, 본문 `Closes #번호`(템플릿 사용) → CI 통과 → `develop` 머지.
- 머지는 사용자가 요청할 때만 `gh pr merge --merge`로 한다.
