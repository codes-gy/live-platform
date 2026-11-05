package com.gy.live.support;

import com.gy.live.livekit.LiveKitException;
import com.gy.live.livekit.LiveKitRoomClient;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** LiveKit 서버 대신 호출을 기록하고, 실패 · 참가자 상태를 흉내 낸다. */
public class FakeLiveKitRoomClient implements LiveKitRoomClient {

	public final List<String> createdRooms = new CopyOnWriteArrayList<>();
	public final List<String> deletedRooms = new CopyOnWriteArrayList<>();
	private final Set<String> participants = ConcurrentHashMap.newKeySet();
	public volatile boolean failCreate;
	public volatile boolean failDelete;

	public void reset() {
		createdRooms.clear();
		deletedRooms.clear();
		participants.clear();
		failCreate = false;
		failDelete = false;
	}

	public void join(String roomName, String identity) {
		participants.add(roomName + "/" + identity);
	}

	@Override
	public void createRoom(String roomName) {
		if (failCreate) {
			throw new LiveKitException("가짜 실패");
		}
		createdRooms.add(roomName);
	}

	@Override
	public void deleteRoom(String roomName) {
		if (failDelete) {
			throw new LiveKitException("가짜 실패");
		}
		deletedRooms.add(roomName);
	}

	@Override
	public boolean hasParticipant(String roomName, String identity) {
		return participants.contains(roomName + "/" + identity);
	}
}
