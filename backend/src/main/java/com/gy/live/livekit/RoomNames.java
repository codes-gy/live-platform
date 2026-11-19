package com.gy.live.livekit;

import java.util.Optional;

/** 방송 하나 = LiveKit 방 하나. 방 이름과 참가자 이름 규칙을 한 곳에 둔다. */
public final class RoomNames {

	private static final String ROOM_PREFIX = "broadcast-";
	private static final String BROADCASTER_PREFIX = "broadcaster-";

	private RoomNames() {
	}

	public static String of(Long broadcastId) {
		return ROOM_PREFIX + broadcastId;
	}

	/** 웹훅으로 받은 방 이름에서 방송 ID를 찾는다. 이 서비스가 만든 방이 아니면 빈 값. */
	public static Optional<Long> broadcastIdOf(String roomName) {
		if (roomName == null || !roomName.startsWith(ROOM_PREFIX)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Long.parseLong(roomName.substring(ROOM_PREFIX.length())));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	public static String broadcasterIdentity(Long userId) {
		return BROADCASTER_PREFIX + userId;
	}

	public static boolean isBroadcaster(String identity) {
		return identity != null && identity.startsWith(BROADCASTER_PREFIX);
	}
}
