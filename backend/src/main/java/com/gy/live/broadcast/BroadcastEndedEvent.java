package com.gy.live.broadcast;

/** 방송이 LIVE에서 ENDED로 바뀐 트랜잭션 안에서 발행한다. 듣는 쪽은 커밋 뒤에 처리한다. */
public record BroadcastEndedEvent(Long broadcastId) {
}
