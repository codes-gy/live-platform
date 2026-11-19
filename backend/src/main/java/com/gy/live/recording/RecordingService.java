package com.gy.live.recording;

import com.gy.live.broadcast.BroadcastRepository;
import com.gy.live.broadcast.BroadcastStartedEvent;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.global.web.PageResponse;
import com.gy.live.livekit.LiveKitRecorder;
import com.gy.live.livekit.RoomNames;
import com.gy.live.recording.dto.RecordingResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import livekit.LivekitEgress.EgressInfo;
import livekit.LivekitEgress.EgressStatus;
import livekit.LivekitEgress.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 녹화: 방송이 시작되면 LiveKit Egress로 방 녹화를 걸고(R2에 MP4), egress_ended 웹훅으로 결과를 저장한다.
 * 녹화는 부가 기능이라 시작에 실패해도 방송은 그대로 진행하고 녹화만 FAILED로 남긴다.
 */
@Service
public class RecordingService {

	private static final Logger log = LoggerFactory.getLogger(RecordingService.class);
	static final int MAX_PAGE_SIZE = 50;
	private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
	private static final long NANOS_PER_MILLI = 1_000_000L;

	private final RecordingRepository recordingRepository;
	private final BroadcastRepository broadcastRepository;
	private final LiveKitRecorder recorder;
	private final RecordingStorage storage;
	private final TaskScheduler scheduler;
	private final TransactionTemplate tx;

	public RecordingService(RecordingRepository recordingRepository, BroadcastRepository broadcastRepository,
			LiveKitRecorder recorder, RecordingStorage storage,
			@Qualifier("liveTaskScheduler") TaskScheduler scheduler, TransactionTemplate tx) {
		this.recordingRepository = recordingRepository;
		this.broadcastRepository = broadcastRepository;
		this.recorder = recorder;
		this.storage = storage;
		this.scheduler = scheduler;
		this.tx = tx;
	}

	/** 방송 시작이 커밋된 뒤, 시작 응답을 늦추지 않게 따로 녹화를 건다. */
	@TransactionalEventListener
	public void onBroadcastStarted(BroadcastStartedEvent event) {
		if (!recorder.enabled()) {
			return;
		}
		scheduler.schedule(() -> startRecording(event.broadcastId()), Instant.now());
	}

	void startRecording(Long broadcastId) {
		String objectKey = "recordings/broadcast-%d/%s.mp4".formatted(broadcastId, KEY_TIME.format(Instant.now()));
		String egressId = null;
		String error = null;
		try {
			egressId = recorder.startRoomRecording(RoomNames.of(broadcastId), objectKey);
		} catch (RuntimeException e) {
			log.warn("녹화 시작 실패(방송은 계속): broadcast={}", broadcastId, e);
			error = "녹화를 시작하지 못했습니다: " + e.getMessage();
		}
		String startedEgress = egressId;
		String startError = error;
		tx.executeWithoutResult(status -> broadcastRepository.findById(broadcastId).ifPresent(broadcast ->
				recordingRepository.save(startedEgress != null
						? Recording.started(broadcast, startedEgress, objectKey)
						: Recording.failedToStart(broadcast, objectKey, startError))));
	}

	/** 웹훅 egress_ended: 파일이 올라갔으면 READY, 아니면 FAILED. 같은 알림이 반복돼도 결과가 같다. */
	@Transactional
	public void onEgressEnded(EgressInfo info) {
		recordingRepository.findForUpdateByEgressId(info.getEgressId()).ifPresentOrElse(recording -> {
			List<FileInfo> files = info.getFileResultsList();
			if (info.getStatus() == EgressStatus.EGRESS_COMPLETE && !files.isEmpty()) {
				FileInfo file = files.getFirst();
				recording.complete(file.getFilename(), file.getDuration() / NANOS_PER_MILLI, file.getSize());
				log.info("녹화 완료: recording={}, {}ms, {}bytes", recording.getId(), recording.getDurationMs(),
						recording.getSizeBytes());
			} else {
				String error = info.getError().isBlank() ? info.getStatus().name() : info.getError();
				recording.fail(error);
				log.warn("녹화 실패: recording={}, {}", recording.getId(), error);
			}
		}, () -> log.info("알 수 없는 녹화 종료 알림: egress={}", info.getEgressId()));
	}

	/** 누구나. 재생할 수 있는 녹화만 최신순. */
	@Transactional(readOnly = true)
	public PageResponse<RecordingResponse> list(int page, int size) {
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "id"));
		return PageResponse.of(recordingRepository.findReady(pageable), RecordingResponse::summary);
	}

	/** 누구나. 재생할 수 있으면 서명된 재생 주소를 함께 준다. */
	@Transactional(readOnly = true)
	public RecordingResponse get(Long recordingId) {
		Recording recording = recordingRepository.findWithBroadcastById(recordingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
		return RecordingResponse.of(recording, recording.isReady() ? storage.playbackUrl(recording.getObjectKey()) : null);
	}

	/** 누구나. 한 방송의 녹화(녹화 중 · 실패 포함) 최신순. */
	@Transactional(readOnly = true)
	public List<RecordingResponse> ofBroadcast(Long broadcastId) {
		if (!broadcastRepository.existsById(broadcastId)) {
			throw new BusinessException(ErrorCode.BROADCAST_NOT_FOUND);
		}
		return recordingRepository.findByBroadcastIdOrderByIdDesc(broadcastId).stream()
				.map(RecordingResponse::summary).toList();
	}
}
