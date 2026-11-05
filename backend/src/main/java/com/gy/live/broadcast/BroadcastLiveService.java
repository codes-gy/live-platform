package com.gy.live.broadcast;

import com.gy.live.broadcast.dto.BroadcastResponse;
import com.gy.live.broadcast.dto.LiveAccessResponse;
import com.gy.live.global.config.AppProperties;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.global.security.LoginUser;
import com.gy.live.livekit.LiveKitException;
import com.gy.live.livekit.LiveKitRoomClient;
import com.gy.live.livekit.LiveKitTokenIssuer;
import com.gy.live.livekit.RoomNames;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 송출 제어: 시작 · 종료 · 접속 토큰 · LiveKit 이벤트로 인한 종료.
 *
 * <p>DB 상태 변경은 행 잠금 안에서 짧게 끝내고, LiveKit 호출(네트워크)은 트랜잭션 밖에서 한다.
 * 시작은 방을 먼저 만들고 나서 LIVE로 바꾼다(방 만들기에 실패하면 상태가 그대로). 종료는 ENDED로 바꾼 뒤
 * 방을 닫는다(방 닫기에 실패해도 방송은 종료로 남고, 남은 방은 LiveKit이 빈 방 시간 뒤 닫는다).
 */
@Service
public class BroadcastLiveService {

	private static final Logger log = LoggerFactory.getLogger(BroadcastLiveService.class);
	private static final String GUEST_NAME = "시청자";

	private final BroadcastService broadcastService;
	private final BroadcastRepository broadcastRepository;
	private final LiveKitRoomClient roomClient;
	private final LiveKitTokenIssuer tokenIssuer;
	private final TransactionTemplate tx;
	private final TaskScheduler scheduler;
	private final Duration broadcasterGrace;

	public BroadcastLiveService(BroadcastService broadcastService, BroadcastRepository broadcastRepository,
			LiveKitRoomClient roomClient, LiveKitTokenIssuer tokenIssuer, TransactionTemplate tx,
			@Qualifier("liveTaskScheduler") TaskScheduler scheduler, AppProperties properties) {
		this.broadcastService = broadcastService;
		this.broadcastRepository = broadcastRepository;
		this.roomClient = roomClient;
		this.tokenIssuer = tokenIssuer;
		this.tx = tx;
		this.scheduler = scheduler;
		this.broadcasterGrace = properties.livekit().broadcasterGrace();
	}

	/** 소유자만. READY → LIVE로 바꾸고 송출 토큰을 준다. */
	public LiveAccessResponse start(LoginUser user, Long broadcastId) {
		// 1) 권한 · 상태를 먼저 확인해서, 남의 방송이나 종료된 방송이면 LiveKit을 부르지 않는다
		tx.executeWithoutResult(status -> requireStartable(broadcastService.findOwned(user.id(), broadcastId)));

		// 2) 방을 미리 만든다: 아무도 안 들어오면 emptyTimeout 뒤 닫히고 웹훅으로 방송이 종료된다
		String roomName = RoomNames.of(broadcastId);
		callLiveKit(() -> roomClient.createRoom(roomName));

		// 3) 잠금 안에서 다시 확인하며 LIVE로 바꾼다(동시에 두 번 눌러도 한 번만 시작)
		BroadcastResponse started = tx.execute(status -> {
			Broadcast broadcast = lockOwned(user.id(), broadcastId);
			broadcast.start();
			return BroadcastResponse.from(broadcast);
		});
		return publisherAccess(user, roomName, started);
	}

	/** 소유자만, 방송 중일 때. 새로고침 · 재접속할 때 송출 토큰을 다시 받는다. */
	public LiveAccessResponse publisherToken(LoginUser user, Long broadcastId) {
		BroadcastResponse broadcast = tx.execute(status -> {
			Broadcast owned = broadcastService.findOwned(user.id(), broadcastId);
			requireLive(owned);
			return BroadcastResponse.from(owned);
		});
		return publisherAccess(user, RoomNames.of(broadcastId), broadcast);
	}

	/** 누구나(로그인 없이도), 방송 중일 때. 받기만 하는 토큰. */
	public LiveAccessResponse viewerToken(LoginUser userOrNull, Long broadcastId) {
		BroadcastResponse broadcast = tx.execute(status -> {
			Broadcast found = broadcastRepository.findWithOwnerById(broadcastId)
					.orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
			requireLive(found);
			return BroadcastResponse.from(found);
		});
		String identity = "viewer-" + UUID.randomUUID();
		String name = userOrNull == null ? GUEST_NAME : userOrNull.nickname();
		String roomName = RoomNames.of(broadcastId);
		return new LiveAccessResponse(tokenIssuer.serverUrl(), roomName, identity,
				tokenIssuer.viewerToken(roomName, identity, name), broadcast);
	}

	/** 소유자만. LIVE → ENDED로 바꾸고 방을 닫아 송출을 끊는다. 이미 종료됐으면 그대로 돌려준다(두 번 눌러도 안전). */
	public BroadcastResponse end(LoginUser user, Long broadcastId) {
		BroadcastResponse ended = tx.execute(status -> {
			Broadcast broadcast = lockOwned(user.id(), broadcastId);
			if (broadcast.getStatus() != BroadcastStatus.ENDED) {
				broadcast.end();
			}
			return BroadcastResponse.from(broadcast);
		});
		closeRoomQuietly(RoomNames.of(broadcastId));
		return ended;
	}

	/** 웹훅 room_finished: 방이 닫혔는데(방송자가 안 돌아옴, 아무도 안 들어옴) 아직 LIVE면 종료로 맞춘다. */
	public void onRoomFinished(String roomName) {
		RoomNames.broadcastIdOf(roomName).ifPresent(id -> endBySystem(id, "방 종료"));
	}

	/**
	 * 웹훅 participant_left(방송자): 시청자가 남아 있으면 방이 닫히지 않으므로 따로 챙긴다.
	 * 새로고침일 수 있어 바로 끝내지 않고, 유예 시간 뒤에도 방송자가 없으면 종료한다.
	 */
	public void onParticipantLeft(String roomName, String identity) {
		if (!RoomNames.isBroadcaster(identity)) {
			return;
		}
		RoomNames.broadcastIdOf(roomName).ifPresent(id ->
				scheduler.schedule(() -> endIfBroadcasterGone(id, identity), Instant.now().plus(broadcasterGrace)));
	}

	void endIfBroadcasterGone(Long broadcastId, String identity) {
		String roomName = RoomNames.of(broadcastId);
		try {
			if (roomClient.hasParticipant(roomName, identity)) {
				return;
			}
		} catch (RuntimeException e) {
			// 확인할 수 없으면 끝내지 않는다. 방이 비면 room_finished가 정리한다
			log.warn("방송자 재접속 확인 실패: broadcast={}", broadcastId, e);
			return;
		}
		if (endBySystem(broadcastId, "방송자 이탈")) {
			closeRoomQuietly(roomName);
		}
	}

	/** @return 이번 호출로 종료했으면 true */
	private boolean endBySystem(Long broadcastId, String reason) {
		Boolean ended = tx.execute(status -> broadcastRepository.findForUpdateById(broadcastId)
				.filter(b -> b.getStatus() == BroadcastStatus.LIVE)
				.map(b -> {
					b.end();
					return true;
				})
				.orElse(false));
		if (Boolean.TRUE.equals(ended)) {
			log.info("방송 자동 종료: broadcast={}, 이유={}", broadcastId, reason);
		}
		return Boolean.TRUE.equals(ended);
	}

	private Broadcast lockOwned(Long userId, Long broadcastId) {
		Broadcast broadcast = broadcastRepository.findForUpdateById(broadcastId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
		if (!broadcast.isOwnedBy(userId)) {
			throw new BusinessException(ErrorCode.NOT_BROADCAST_OWNER);
		}
		return broadcast;
	}

	private LiveAccessResponse publisherAccess(LoginUser user, String roomName, BroadcastResponse broadcast) {
		String identity = RoomNames.broadcasterIdentity(user.id());
		return new LiveAccessResponse(tokenIssuer.serverUrl(), roomName, identity,
				tokenIssuer.publisherToken(roomName, identity, user.nickname()), broadcast);
	}

	private static void requireStartable(Broadcast broadcast) {
		switch (broadcast.getStatus()) {
			case LIVE -> throw new BusinessException(ErrorCode.BROADCAST_ALREADY_LIVE);
			case ENDED -> throw new BusinessException(ErrorCode.BROADCAST_ENDED);
			case READY -> {
			}
		}
	}

	private static void requireLive(Broadcast broadcast) {
		if (broadcast.getStatus() != BroadcastStatus.LIVE) {
			throw new BusinessException(broadcast.getStatus() == BroadcastStatus.ENDED
					? ErrorCode.BROADCAST_ENDED : ErrorCode.BROADCAST_NOT_LIVE);
		}
	}

	private void callLiveKit(Runnable call) {
		try {
			call.run();
		} catch (LiveKitException e) {
			log.error("LiveKit 호출 실패", e);
			throw new BusinessException(ErrorCode.LIVEKIT_UNAVAILABLE);
		}
	}

	private void closeRoomQuietly(String roomName) {
		try {
			roomClient.deleteRoom(roomName);
		} catch (RuntimeException e) {
			log.warn("방 닫기 실패(방송은 종료됨, 빈 방은 LiveKit이 닫는다): room={}", roomName, e);
		}
	}
}
