package com.gy.live.broadcast.dto;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.broadcast.BroadcastStatus;
import java.time.Instant;

public record BroadcastResponse(
		Long id,
		String title,
		String description,
		BroadcastStatus status,
		Owner owner,
		Instant startedAt,
		Instant endedAt,
		Instant createdAt,
		Instant updatedAt,
		/** 썸네일이 없으면 null. 카드가 이 값을 썸네일 주소의 버전으로 쓴다 */
		Instant thumbnailUpdatedAt
) {

	public record Owner(Long id, String nickname) {
	}

	public static BroadcastResponse from(Broadcast b) {
		return new BroadcastResponse(b.getId(), b.getTitle(), b.getDescription(), b.getStatus(),
				new Owner(b.getOwner().getId(), b.getOwner().getNickname()),
				b.getStartedAt(), b.getEndedAt(), b.getCreatedAt(), b.getUpdatedAt(), b.getThumbnailUpdatedAt());
	}
}
