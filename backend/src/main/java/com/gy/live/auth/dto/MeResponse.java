package com.gy.live.auth.dto;

import com.gy.live.user.User;

public record MeResponse(Long id, String email, String nickname) {

	public static MeResponse from(User user) {
		return new MeResponse(user.getId(), user.getEmail(), user.getNickname());
	}
}
