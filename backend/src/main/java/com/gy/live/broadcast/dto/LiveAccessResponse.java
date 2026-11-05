package com.gy.live.broadcast.dto;

/**
 * 프론트가 LiveKit 방에 들어갈 때 쓰는 값.
 *
 * @param serverUrl LiveKit 접속 주소(wss://…)
 * @param token     권한이 담긴 접속 토큰. 송출용은 방송 소유자에게만 발급된다
 */
public record LiveAccessResponse(String serverUrl, String roomName, String identity, String token,
		BroadcastResponse broadcast) {
}
