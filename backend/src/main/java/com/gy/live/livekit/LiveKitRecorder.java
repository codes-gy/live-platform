package com.gy.live.livekit;

/** LiveKit Egress로 방 전체(화면 · 소리)를 MP4로 녹화해 저장소(R2)에 올린다. 테스트에서는 가짜로 바꾼다. */
public interface LiveKitRecorder {

	/** 녹화 설정(R2 키)이 있는지. 없으면 녹화를 시도하지 않는다. */
	boolean enabled();

	/**
	 * 방 녹화를 시작한다. 방이 닫히면 LiveKit이 녹화를 끝내고 egress_ended 웹훅을 보낸다.
	 *
	 * @param objectKey 버킷 안에 저장할 파일 위치
	 * @return LiveKit egress ID. 실패하면 {@link LiveKitException}
	 */
	String startRoomRecording(String roomName, String objectKey);
}
