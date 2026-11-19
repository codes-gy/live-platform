package com.gy.live.broadcast.thumbnail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 읽기용. 쓰기는 동시 첫 업로드에도 충돌하지 않도록 BroadcastThumbnailRepository.upsert로 한다. */
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

	public byte[] getImage() {
		return image;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
