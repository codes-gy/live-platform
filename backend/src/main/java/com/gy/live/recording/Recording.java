package com.gy.live.recording;

import com.gy.live.broadcast.Broadcast;
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

/** 방송 한 회차의 녹화 파일. 파일은 R2에 있고 여기에는 위치와 정보만 둔다. */
@Entity
@Table(name = "recordings")
public class Recording {

	private static final int ERROR_MAX = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broadcast_id")
	private Broadcast broadcast;

	@Column(name = "egress_id", unique = true)
	private String egressId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RecordingStatus status;

	@Column(name = "object_key", nullable = false)
	private String objectKey;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "size_bytes")
	private Long sizeBytes;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	protected Recording() {
	}

	private Recording(Broadcast broadcast, String egressId, RecordingStatus status, String objectKey) {
		this.broadcast = broadcast;
		this.egressId = egressId;
		this.status = status;
		this.objectKey = objectKey;
		this.createdAt = Instant.now();
	}

	public static Recording started(Broadcast broadcast, String egressId, String objectKey) {
		return new Recording(broadcast, egressId, RecordingStatus.RECORDING, objectKey);
	}

	/** 녹화를 시작하지도 못한 경우. 방송은 계속되고 녹화만 실패로 남긴다. */
	public static Recording failedToStart(Broadcast broadcast, String objectKey, String error) {
		Recording recording = new Recording(broadcast, null, RecordingStatus.FAILED, objectKey);
		recording.errorMessage = truncate(error);
		recording.endedAt = recording.createdAt;
		return recording;
	}

	/** egress_ended: 파일이 올라갔다. 같은 알림이 다시 와도 결과가 같다. */
	public void complete(String objectKey, long durationMs, long sizeBytes) {
		if (status == RecordingStatus.READY) {
			return;
		}
		this.status = RecordingStatus.READY;
		if (objectKey != null && !objectKey.isBlank()) {
			this.objectKey = objectKey;
		}
		this.durationMs = durationMs;
		this.sizeBytes = sizeBytes;
		this.errorMessage = null;
		this.endedAt = Instant.now();
	}

	public void fail(String error) {
		if (status != RecordingStatus.RECORDING) {
			return;
		}
		this.status = RecordingStatus.FAILED;
		this.errorMessage = truncate(error);
		this.endedAt = Instant.now();
	}

	private static String truncate(String error) {
		if (error == null || error.isBlank()) {
			return "알 수 없는 오류";
		}
		return error.length() > ERROR_MAX ? error.substring(0, ERROR_MAX) : error;
	}

	public boolean isReady() {
		return status == RecordingStatus.READY;
	}

	public Long getId() {
		return id;
	}

	public Broadcast getBroadcast() {
		return broadcast;
	}

	public String getEgressId() {
		return egressId;
	}

	public RecordingStatus getStatus() {
		return status;
	}

	public String getObjectKey() {
		return objectKey;
	}

	public Long getDurationMs() {
		return durationMs;
	}

	public Long getSizeBytes() {
		return sizeBytes;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}
}
