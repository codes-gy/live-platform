package com.gy.live.support;

import com.gy.live.livekit.LiveKitException;
import com.gy.live.livekit.LiveKitRecorder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** LiveKit Egress 대신 녹화 시작 요청을 기록한다. */
public class FakeLiveKitRecorder implements LiveKitRecorder {

	public record Started(String roomName, String objectKey, String egressId) {
	}

	public final List<Started> started = new CopyOnWriteArrayList<>();
	public volatile boolean enabled = true;
	public volatile boolean failStart;

	public void reset() {
		started.clear();
		enabled = true;
		failStart = false;
	}

	@Override
	public boolean enabled() {
		return enabled;
	}

	@Override
	public String startRoomRecording(String roomName, String objectKey) {
		if (failStart) {
			throw new LiveKitException("가짜 녹화 실패");
		}
		String egressId = "EG_" + UUID.randomUUID();
		started.add(new Started(roomName, objectKey, egressId));
		return egressId;
	}
}
