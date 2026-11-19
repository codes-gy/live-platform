package com.gy.live.comment;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "comments")
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broadcast_id", nullable = false, updatable = false)
	private Broadcast broadcast;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false, updatable = false)
	private User author;

	@Column(nullable = false, length = 500)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** 수정한 적이 없으면 null. */
	@Column(name = "updated_at")
	private Instant updatedAt;

	protected Comment() {
	}

	public Comment(Broadcast broadcast, User author, String content) {
		this.broadcast = Objects.requireNonNull(broadcast);
		this.author = Objects.requireNonNull(author);
		this.content = content;
		this.createdAt = Instant.now();
	}

	public boolean isWrittenBy(Long userId) {
		return author.getId().equals(userId);
	}

	public void edit(String content) {
		this.content = content;
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Broadcast getBroadcast() {
		return broadcast;
	}

	public User getAuthor() {
		return author;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
