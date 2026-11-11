package com.gy.live.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 댓글 작성 · 수정 공통. */
public record CommentRequest(
		@NotBlank(message = "댓글 내용을 입력해 주세요.")
		@Size(max = 500, message = "댓글은 500자 이하로 입력해 주세요.")
		String content
) {

	public String normalizedContent() {
		return content.strip();
	}
}
