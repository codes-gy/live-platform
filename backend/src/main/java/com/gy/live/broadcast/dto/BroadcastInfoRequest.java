package com.gy.live.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 방송 등록 · 수정 공통. 수정은 제목 · 설명을 통째로 바꾼다. */
public record BroadcastInfoRequest(
		@NotBlank(message = "방송 제목을 입력해 주세요.")
		@Size(max = 100, message = "방송 제목은 100자 이하로 입력해 주세요.")
		String title,

		@Size(max = 1000, message = "방송 설명은 1000자 이하로 입력해 주세요.")
		String description
) {

	public String normalizedTitle() {
		return title.strip();
	}

	/** 빈 설명은 없는 것으로 저장한다. */
	public String normalizedDescription() {
		return description == null || description.isBlank() ? null : description.strip();
	}
}
