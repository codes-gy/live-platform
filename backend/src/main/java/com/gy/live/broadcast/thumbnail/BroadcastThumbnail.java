package com.gy.live.broadcast.thumbnail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "broadcast_thumbnails")
public class BroadcastThumbnail {

	@Id
	@Column(name = "broadcast_id")
	private Long broadcastId;

	@Column(nullable = false)
	private byte[] image;

	@Column(nullable = false)
	private Instant updatedAt;

	protected BroadcastThumbnail() {
	}

	BroadcastThumbnail(Long broadcastId, byte[] image) {
		this.broadcastId = broadcastId;
		replace(image);
	}

	void replace(byte[] image) {
		this.image = image;
		this.updatedAt = Instant.now();
	}

	public byte[] getImage() {
		return image;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
