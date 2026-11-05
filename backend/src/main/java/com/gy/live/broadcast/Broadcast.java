package com.gy.live.broadcast;

import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "broadcasts")
public class Broadcast {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private User owner;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(length = 1000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private BroadcastStatus status;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Broadcast() {
	}

	public Broadcast(User owner, String title, String description) {
		this.owner = Objects.requireNonNull(owner);
		this.title = title;
		this.description = description;
		this.status = BroadcastStatus.READY;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public boolean isOwnedBy(Long userId) {
		return owner.getId().equals(userId);
	}

	public void updateInfo(String title, String description) {
		this.title = title;
		this.description = description;
		this.updatedAt = Instant.now();
	}

	/** 송출 시작. READY에서만 LIVE가 된다. */
	public void start() {
		switch (status) {
			case LIVE -> throw new BusinessException(ErrorCode.BROADCAST_ALREADY_LIVE);
			case ENDED -> throw new BusinessException(ErrorCode.BROADCAST_ENDED);
			case READY -> {
				this.status = BroadcastStatus.LIVE;
				this.startedAt = Instant.now();
				this.updatedAt = this.startedAt;
			}
		}
	}

	/** 송출 종료. LIVE에서만 ENDED가 된다. */
	public void end() {
		if (status != BroadcastStatus.LIVE) {
			throw new BusinessException(status == BroadcastStatus.ENDED
					? ErrorCode.BROADCAST_ENDED : ErrorCode.BROADCAST_NOT_LIVE);
		}
		this.status = BroadcastStatus.ENDED;
		this.endedAt = Instant.now();
		this.updatedAt = this.endedAt;
	}

	public void checkDeletable() {
		if (status == BroadcastStatus.LIVE) {
			throw new BusinessException(ErrorCode.BROADCAST_LIVE_CANNOT_DELETE);
		}
	}

	public Long getId() {
		return id;
	}

	public User getOwner() {
		return owner;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public BroadcastStatus getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
