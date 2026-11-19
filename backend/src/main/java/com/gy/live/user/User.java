package com.gy.live.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false, length = 30)
	private String nickname;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** 프로필 이미지 업로드가 직접 SQL로 바꾼다. 엔티티 저장이 덮어쓰지 않게 읽기 전용으로 둔다. */
	@Column(name = "avatar_updated_at", insertable = false, updatable = false)
	private Instant avatarUpdatedAt;

	protected User() {
	}

	public User(String email, String passwordHash, String nickname) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.nickname = nickname;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getNickname() {
		return nickname;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getAvatarUpdatedAt() {
		return avatarUpdatedAt;
	}
}
