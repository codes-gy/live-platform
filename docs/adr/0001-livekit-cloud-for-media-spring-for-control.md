# 미디어는 LiveKit Cloud(SFU)에 맡기고, Spring은 제어만 한다

브라우저에서 화면 · 카메라를 1:N 저지연으로 송출해야 했다. P2P WebRTC는 시청자 수에 한계가 있고, RTMP/HLS는 지연이 수 초이며 브라우저 송출이 어렵고, 미디어 서버를 직접 운영할 여력은 없었다. 그래서 영상 · 소리는 브라우저 ↔ LiveKit Cloud로만 흐르고, Spring은 권한 판단(송출 · 시청 토큰 발급), 방 생성 · 삭제, 웹훅 처리만 맡는다. LiveKit SDK는 `livekit/` 패키지 밖으로 나가지 않는다.

근거: PR #18(#3), PROGRESS.md "확정 결정 사항(2026-11-05)", [docs/DESIGN.md](../DESIGN.md) 8절 · [docs/ARCHITECTURE.md](../ARCHITECTURE.md) 2절.

## Consequences

- "누가 송출할 수 있는가"는 토큰 발급 시점에, "송출을 끊는다"는 방 삭제로 표현된다(ADR 0002).
- 녹화도 LiveKit Egress에 묶인다. 무료 플랜 한도(녹화 월 60분)가 곧 서비스 한도다.
