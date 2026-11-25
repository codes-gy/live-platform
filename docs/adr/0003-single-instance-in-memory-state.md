# 서버 한 대를 전제로 실시간 상태를 메모리에 둔다

댓글 SSE 구독 목록(`CommentStreams`), 방송자 이탈 유예 타이머(`BroadcastLiveService`의 `liveTaskScheduler`), 로그인 실패 · SSE 동시 연결 제한은 모두 서버 메모리에 있다. 공모전 일정과 512MB 다이노 한 대 운영에서 Redis 같은 공유 저장소는 비용과 작업량에 비해 얻는 것이 없어서다. 재시작으로 사라지는 유예 타이머는 LiveKit `emptyTimeout` · `departureTimeout`과 1분 주기 `LiveReconcileScheduler`가 안전망으로 메운다.

근거: [docs/DESIGN.md](../DESIGN.md) 5.4절 · 9절, PR #35(#34), PR #72, 후속 이슈 #71(Redis Pub/Sub).

## Consequences

- 서버를 두 대 이상으로 늘리면 다른 서버에서 쓴 댓글이 SSE로 오지 않고, 요청 제한은 대수만큼 느슨해진다. 늘리기 전에 #71을 먼저 해야 한다.
- 요청 제한은 `X-Forwarded-For` 첫 값을 믿으므로 1차 방어일 뿐이다.
