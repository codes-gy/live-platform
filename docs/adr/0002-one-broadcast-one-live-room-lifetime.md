# 방송 하나는 라이브 한 회차이고, LiveKit 방은 방송 중일 때만 존재한다

방송은 방송 전 → 방송 중 → 종료를 한 번만 거치고 종료된 방송은 다시 시작하지 않는다. LiveKit 방(`broadcast-{id}`)은 시작 API에서 방 생성이 성공해야만 방송 중으로 바뀌고, 모든 종료 경로(방송자 종료 · 웹훅 · 이탈 유예 만료 · 주기 정리)는 `BroadcastLiveService.endLocked()`를 지나 방을 삭제한다. 토큰 만료만으로 막는 방식은 즉시 끊기지 않아 버렸고, 재시작을 허용하면 댓글 · 후원 · 녹화를 회차별로 나눠야 해서 버렸다.

근거: `Broadcast.start()` · `end()`, `BroadcastStatus`, `RoomNames`, PR #18(#3) · PR #35(#34), [docs/DESIGN.md](../DESIGN.md) 2절 · 5.3절 · 8절.

## Consequences

- 같은 사람이 다시 방송하려면 새 방송을 등록해야 한다. 댓글 · 후원 · 녹화는 모두 방송 id 하나에 묶인다.
- 녹화 종료를 따로 부르지 않는다. 방이 닫히면 Egress가 스스로 끝나고 `egress_ended` 웹훅이 온다.
