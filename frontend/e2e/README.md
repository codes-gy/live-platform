# E2E (Playwright)

실제로 띄운 서비스(프론트 · 백엔드 · LiveKit)를 브라우저로 확인한다. 가짜 카메라 · 마이크로 송출하므로 장치가 없어도 된다.

```bash
cd frontend
npx playwright install chromium            # 처음 한 번
E2E_BASE_URL=http://localhost:3000 npm run e2e
E2E_BASE_URL=https://onair-live-web-28fc2f0d1715.herokuapp.com npm run e2e   # 운영 확인
```

| 파일 | 확인하는 것 |
|---|---|
| `auth.spec.ts` | 비로그인 목록 조회, 보호 화면 → 로그인 이동 · API 401, 다른 사용자의 수정 · 삭제 · 송출 403과 "송출할 권한이 없습니다" |
| `live.spec.ts` | 카메라 송출 → LIVE, 비로그인 시청자 영상 · 오디오 수신, 다른 사용자 댓글이 시청자 화면에 실시간 도착, 종료 → 서버 ENDED · 시청 화면 종료 |

- LiveKit 키가 설정된 서버가 필요하다. 사용자는 실행마다 회원가입으로 새로 만들고(`e2e-…@example.com`), 만든 방송은 끝나면 지운다.
- CI는 백엔드 테스트 · 프론트 lint · build만 돌린다(E2E는 LiveKit 키와 실행 중인 서버가 필요해서 수동).
