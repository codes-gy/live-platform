package com.gy.live.broadcast;

/** LIVE 방송 정리에 필요한 최소 정보: 방 이름은 id, 방송자 참가자 이름은 ownerId에서 나온다. */
record LiveBroadcastRef(Long id, Long ownerId) {
}
