package com.gy.live.broadcast;

/** 방송이 READY에서 LIVE로 바뀐 트랜잭션 안에서 발행한다. 듣는 쪽(녹화)은 커밋 뒤에 처리한다. */
public record BroadcastStartedEvent(Long broadcastId) {
}
