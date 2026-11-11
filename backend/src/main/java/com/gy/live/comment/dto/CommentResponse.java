package com.gy.live.comment.dto;

import com.gy.live.comment.Comment;
import java.time.Instant;

/** @param updatedAt 수정한 적이 없으면 null */
public record CommentResponse(
		Long id,
		Long broadcastId,
		Author author,
		String content,
		Instant createdAt,
		Instant updatedAt
) {

	public record Author(Long id, String nickname) {
	}

	public static CommentResponse from(Comment c) {
		return new CommentResponse(c.getId(), c.getBroadcast().getId(),
				new Author(c.getAuthor().getId(), c.getAuthor().getNickname()),
				c.getContent(), c.getCreatedAt(), c.getUpdatedAt());
	}
}
