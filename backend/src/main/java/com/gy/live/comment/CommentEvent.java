package com.gy.live.comment;

import com.gy.live.comment.dto.CommentResponse;

/** 댓글 변경을 실시간 구독자에게 알린다. 트랜잭션 안에서 발행하고 커밋 뒤에 보낸다. */
sealed interface CommentEvent {

	Long broadcastId();

	record Created(CommentResponse comment) implements CommentEvent {
		@Override
		public Long broadcastId() {
			return comment.broadcastId();
		}
	}

	record Updated(CommentResponse comment) implements CommentEvent {
		@Override
		public Long broadcastId() {
			return comment.broadcastId();
		}
	}

	record Deleted(Long broadcastId, Long commentId) implements CommentEvent {
	}
}
