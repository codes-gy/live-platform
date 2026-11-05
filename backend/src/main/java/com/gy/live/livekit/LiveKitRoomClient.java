package com.gy.live.livekit;

/** LiveKit 서버 API 중 방송에 필요한 것만. 테스트에서는 가짜로 바꾼다. */
public interface LiveKitRoomClient {

	/** 방을 미리 만든다. 이미 있으면 그대로 둔다. 실패하면 {@link LiveKitException}. */
	void createRoom(String roomName);

	/** 방을 닫아 모든 참가자를 내보낸다(송출 강제 중단). 없는 방이면 조용히 넘어간다. */
	void deleteRoom(String roomName);

	/** 그 참가자가 지금 방에 있는지. 방이 없으면 false. */
	boolean hasParticipant(String roomName, String identity);
}
