package com.gy.live.recording.dto;

import com.gy.live.broadcast.dto.BroadcastResponse;
import com.gy.live.recording.Recording;
import com.gy.live.recording.RecordingStatus;
import java.time.Instant;

/**
 * 녹화 하나. 목록에서는 playbackUrl이 null이고, 상세에서 재생할 수 있을 때만 서명된 주소가 들어간다.
 *
 * @param durationMs 녹화 길이(밀리초). 녹화 중이거나 실패면 null
 * @param sizeBytes  파일 크기. 녹화 중이거나 실패면 null
 */
public record RecordingResponse(
		Long id,
		RecordingStatus status,
		BroadcastResponse broadcast,
		Long durationMs,
		Long sizeBytes,
		Instant createdAt,
		Instant endedAt,
		String playbackUrl
) {

	public static RecordingResponse of(Recording r, String playbackUrl) {
		return new RecordingResponse(r.getId(), r.getStatus(), BroadcastResponse.from(r.getBroadcast()),
				r.getDurationMs(), r.getSizeBytes(), r.getCreatedAt(), r.getEndedAt(), playbackUrl);
	}

	public static RecordingResponse summary(Recording r) {
		return of(r, null);
	}
}
