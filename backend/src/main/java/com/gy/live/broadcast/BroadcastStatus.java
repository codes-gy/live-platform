package com.gy.live.broadcast;

/** READY(등록됨) → LIVE(송출 중) → ENDED(종료). 종료된 방송은 다시 시작하지 않는다. */
public enum BroadcastStatus {
	READY, LIVE, ENDED
}
