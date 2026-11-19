package com.gy.live.user.avatar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 읽기용. 쓰기는 UserAvatarRepository의 SQL로 한다. */
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false)
	private byte[] image;

	@Column(nullable = false)
	private Instant updatedAt;

	protected UserAvatar() {
	}

	public byte[] getImage() {
		return image;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
