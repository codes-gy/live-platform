package com.gy.live.comment;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.broadcast.BroadcastEndedEvent;
import com.gy.live.broadcast.BroadcastRepository;
import com.gy.live.broadcast.BroadcastStatus;
import com.gy.live.global.config.AppProperties;
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
 *
 * <p>연결 하나가 서버 자원(소켓 · 메모리)을 30분까지 붙잡으므로, 접속 IP 하나가 동시에 열 수 있는 연결 수를
 * {@code app.comment-stream.max-per-ip}로 제한한다(넘으면 429). 이 수도 메모리에 세므로 서버 한 대 기준이다.
 */
@Component
public class CommentStreams {

	static final Duration TIMEOUT = Duration.ofMinutes(30);
	/** Nginx · 로드밸런서가 조용한 연결을 끊지 않도록(보통 60초) 그보다 자주 주석 한 줄을 보낸다. */
	static final Duration HEARTBEAT = Duration.ofSeconds(20);
	private static final long RECONNECT_MILLIS = 3_000;

	static final int DEFAULT_MAX_PER_IP = 30;

	private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
	/** 연결별 접속 IP와 IP별 연결 수. 연결이 끝나면(remove) 한 번만 줄인다. */
	private final Map<SseEmitter, String> clientIps = new ConcurrentHashMap<>();
	private final Map<String, Integer> perIp = new ConcurrentHashMap<>();
	private final int maxPerIp;
	private final BroadcastRepository broadcastRepository;
	private final ScheduledFuture<?> heartbeat;

	public CommentStreams(BroadcastRepository broadcastRepository,
			@Qualifier("liveTaskScheduler") TaskScheduler scheduler, AppProperties props) {
		this.broadcastRepository = broadcastRepository;
		this.maxPerIp = props.commentStream() == null ? DEFAULT_MAX_PER_IP : props.commentStream().maxPerIp();
		if (maxPerIp < 1) {
			throw new IllegalArgumentException("app.comment-stream.max-per-ip는 1 이상이어야 합니다.");
		}
		this.heartbeat = scheduler.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT);
	}

	/** clientIp: 연결 수를 세는 접속 IP. 이미 maxPerIp개를 열고 있으면 429. */
	public SseEmitter subscribe(Long broadcastId, String clientIp) {
		String ip = clientIp == null ? "" : clientIp;
		if (!acquire(ip)) {
			throw new BusinessException(ErrorCode.TOO_MANY_STREAMS);
		}
		SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
		clientIps.put(emitter, ip);
		emitter.onCompletion(() -> remove(broadcastId, emitter));
		emitter.onError(e -> remove(broadcastId, emitter));
		emitter.onTimeout(emitter::complete);
		// 등록한 다음에 상태를 읽는다: 그 사이 종료가 커밋되면 상태로든 ended 이벤트로든 반드시 알게 된다
		subscribers.compute(broadcastId, (id, set) -> {
			Set<SseEmitter> emitters = set == null ? ConcurrentHashMap.newKeySet() : set;
			emitters.add(emitter);
			return emitters;
		});
		BroadcastStatus status;
		try {
			status = broadcastRepository.findById(broadcastId).map(Broadcast::getStatus).orElse(null);
		} catch (RuntimeException e) {
			remove(broadcastId, emitter);
			throw e;
		}
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

	int connectionCount(String clientIp) {
		return perIp.getOrDefault(clientIp, 0);
	}

	@PreDestroy
	void closeAll() {
		heartbeat.cancel(false);
		subscribers.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
		subscribers.clear();
		clientIps.clear();
		perIp.clear();
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
		// onCompletion · onError · 보내기 실패가 모두 remove를 부를 수 있다. IP 수는 처음 한 번만 줄인다
		String ip = clientIps.remove(emitter);
		if (ip != null) {
			release(ip);
		}
	}

	private boolean acquire(String ip) {
		boolean[] acquired = {false};
		perIp.compute(ip, (k, count) -> {
			int current = count == null ? 0 : count;
			if (current >= maxPerIp) {
				return count;
			}
			acquired[0] = true;
			return current + 1;
		});
		return acquired[0];
	}

	private void release(String ip) {
		perIp.computeIfPresent(ip, (k, count) -> count <= 1 ? null : count - 1);
	}
}
