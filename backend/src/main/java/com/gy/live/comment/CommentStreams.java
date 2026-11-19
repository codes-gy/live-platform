package com.gy.live.comment;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.broadcast.BroadcastEndedEvent;
import com.gy.live.broadcast.BroadcastRepository;
import com.gy.live.broadcast.BroadcastStatus;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 방송별 댓글 실시간 구독(SSE). 서버 한 대 배포라 구독 목록을 메모리에 둔다.
 *
 * <p>보내는 이벤트: {@code comment}(새 댓글) · {@code comment-updated} · {@code comment-deleted}{"id"} ·
 * {@code ended}{"broadcastId"}(그 뒤 연결을 닫는다). 모두 DB 커밋 뒤에 보낸다.
 * 연결이 끊겨 브라우저가 다시 붙으면 그 사이 댓글은 목록 API로 다시 받는다(이벤트 재전송은 하지 않는다).
 */
@Component
public class CommentStreams {

	static final Duration TIMEOUT = Duration.ofMinutes(30);
	/** Nginx · 로드밸런서가 조용한 연결을 끊지 않도록(보통 60초) 그보다 자주 주석 한 줄을 보낸다. */
	static final Duration HEARTBEAT = Duration.ofSeconds(20);
	private static final long RECONNECT_MILLIS = 3_000;

	private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
	private final BroadcastRepository broadcastRepository;
	private final ScheduledFuture<?> heartbeat;

	public CommentStreams(BroadcastRepository broadcastRepository,
			@Qualifier("liveTaskScheduler") TaskScheduler scheduler) {
		this.broadcastRepository = broadcastRepository;
		this.heartbeat = scheduler.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT);
	}

	public SseEmitter subscribe(Long broadcastId) {
		SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
		emitter.onCompletion(() -> remove(broadcastId, emitter));
		emitter.onError(e -> remove(broadcastId, emitter));
		emitter.onTimeout(emitter::complete);
		// 등록한 다음에 상태를 읽는다: 그 사이 종료가 커밋되면 상태로든 ended 이벤트로든 반드시 알게 된다
		subscribers.compute(broadcastId, (id, set) -> {
			Set<SseEmitter> emitters = set == null ? ConcurrentHashMap.newKeySet() : set;
			emitters.add(emitter);
			return emitters;
		});
		BroadcastStatus status = broadcastRepository.findById(broadcastId).map(Broadcast::getStatus).orElse(null);
		if (status == null) {
			remove(broadcastId, emitter);
			throw new BusinessException(ErrorCode.BROADCAST_NOT_FOUND);
		}
		send(broadcastId, emitter, SseEmitter.event().reconnectTime(RECONNECT_MILLIS).comment("connected").build());
		if (status == BroadcastStatus.ENDED) {
			sendEndedAndClose(broadcastId, emitter);
		}
		return emitter;
	}

	@TransactionalEventListener
	void onComment(CommentEvent event) {
		SseEmitter.SseEventBuilder builder = switch (event) {
			case CommentEvent.Created created -> SseEmitter.event().name("comment")
					.data(created.comment(), MediaType.APPLICATION_JSON);
			case CommentEvent.Updated updated -> SseEmitter.event().name("comment-updated")
					.data(updated.comment(), MediaType.APPLICATION_JSON);
			case CommentEvent.Deleted deleted -> SseEmitter.event().name("comment-deleted")
					.data(Map.of("id", deleted.commentId()), MediaType.APPLICATION_JSON);
		};
		Set<DataWithMediaType> items = builder.build();
		emittersOf(event.broadcastId()).forEach(emitter -> send(event.broadcastId(), emitter, items));
	}

	@TransactionalEventListener
	void onBroadcastEnded(BroadcastEndedEvent event) {
		Set<SseEmitter> emitters = subscribers.remove(event.broadcastId());
		if (emitters != null) {
			emitters.forEach(emitter -> sendEndedAndClose(event.broadcastId(), emitter));
		}
	}

	void sendHeartbeat() {
		Set<DataWithMediaType> ping = SseEmitter.event().comment("ping").build();
		subscribers.forEach((broadcastId, emitters) -> emitters.forEach(emitter -> send(broadcastId, emitter, ping)));
	}

	int subscriberCount(Long broadcastId) {
		return emittersOf(broadcastId).size();
	}

	@PreDestroy
	void closeAll() {
		heartbeat.cancel(false);
		subscribers.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
		subscribers.clear();
	}

	private void sendEndedAndClose(Long broadcastId, SseEmitter emitter) {
		remove(broadcastId, emitter);
		send(broadcastId, emitter, SseEmitter.event().name("ended")
				.data(Map.of("broadcastId", broadcastId), MediaType.APPLICATION_JSON).build());
		emitter.complete();
	}

	private Set<SseEmitter> emittersOf(Long broadcastId) {
		return subscribers.getOrDefault(broadcastId, Set.of());
	}

	private void send(Long broadcastId, SseEmitter emitter, Set<DataWithMediaType> items) {
		try {
			emitter.send(items);
		} catch (IOException | IllegalStateException e) {
			// 끊긴 연결(IOException)이나 이미 닫힌 연결. 컨테이너가 onError · onCompletion도 부른다
			remove(broadcastId, emitter);
		}
	}

	private void remove(Long broadcastId, SseEmitter emitter) {
		subscribers.computeIfPresent(broadcastId, (id, emitters) -> {
			emitters.remove(emitter);
			return emitters.isEmpty() ? null : emitters;
		});
	}
}
