package com.gy.live.auth.dto;

import com.gy.live.user.User;
import java.time.Instant;

/** avatarUpdatedAt: 프로필 이미지가 없으면 null. 이미지 주소의 버전으로 쓴다 */
public record MeResponse(Long id, String email, String nickname, Instant avatarUpdatedAt) {

	public static MeResponse from(User user) {
		return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getAvatarUpdatedAt());
	}
}
