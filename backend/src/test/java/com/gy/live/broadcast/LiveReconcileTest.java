package com.gy.live.broadcast;

import static org.assertj.core.api.Assertions.assertThat;

import com.gy.live.livekit.RoomNames;
import com.gy.live.support.FakeLiveKitRoomClient;
import com.gy.live.support.IntegrationTest;
import com.gy.live.user.User;
import com.gy.live.user.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 테스트의 유예 시간은 200ms. 정리 실행은 타이머 대신 reconcileLive()를 직접 부른다. */
@IntegrationTest
class LiveReconcileTest {

	@Autowired
	BroadcastLiveService liveService;

	@Autowired
	BroadcastRepository broadcastRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	FakeLiveKitRoomClient liveKit;

	User owner;

	@BeforeEach
	void setUp() {
		liveKit.reset();
		owner = userRepository.save(new User(UUID.randomUUID() + "@example.com", "hash", "방송주인"));
	}

	private Broadcast liveBroadcast(Instant startedAt) {
		Broadcast broadcast = new Broadcast(owner, "라이브", null);
		broadcast.start();
		broadcast = broadcastRepository.saveAndFlush(broadcast);
		jdbc.update("update broadcasts set started_at = ? where id = ?", Timestamp.from(startedAt), broadcast.getId());
		return broadcast;
	}

	private Broadcast staleLive() {
		return liveBroadcast(Instant.now().minus(1, ChronoUnit.HOURS));
	}

	private void broadcasterJoins(Broadcast broadcast) {
		liveKit.join(RoomNames.of(broadcast.getId()), RoomNames.broadcasterIdentity(owner.getId()));
	}

	private BroadcastStatus statusOf(Broadcast broadcast) {
		return broadcastRepository.findById(broadcast.getId()).orElseThrow().getStatus();
	}

	private void awaitStatus(Broadcast broadcast, BroadcastStatus expected) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 3_000;
		while (statusOf(broadcast) != expected && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
		assertThat(statusOf(broadcast)).isEqualTo(expected);
	}

	@Test
	void 방송자가_방에_없는_오래된_LIVE는_유예_뒤_종료하고_방을_닫는다() throws Exception {
		Broadcast broadcast = staleLive();

		liveService.reconcileLive();

		assertThat(statusOf(broadcast)).as("유예 전에는 아직 LIVE").isEqualTo(BroadcastStatus.LIVE);
		awaitStatus(broadcast, BroadcastStatus.ENDED);
		assertThat(liveKit.deletedRooms).contains(RoomNames.of(broadcast.getId()));
	}

	@Test
	void 방송자가_방에_있으면_LIVE로_둔다() throws Exception {
		Broadcast broadcast = staleLive();
		broadcasterJoins(broadcast);

		liveService.reconcileLive();
		Thread.sleep(500);

		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.LIVE);
		assertThat(liveKit.deletedRooms).doesNotContain(RoomNames.of(broadcast.getId()));
	}

	@Test
	void 새로고침처럼_유예_안에_돌아오면_LIVE로_둔다() throws Exception {
		Broadcast broadcast = staleLive();

		liveService.reconcileLive();
		broadcasterJoins(broadcast);
		Thread.sleep(500);

		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.LIVE);
	}

	@Test
	void 시작한_지_유예가_지나지_않은_LIVE는_건드리지_않는다() throws Exception {
		Broadcast broadcast = liveBroadcast(Instant.now().plus(1, ChronoUnit.HOURS));

		liveService.reconcileLive();
		Thread.sleep(500);

		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.LIVE);
	}

	@Test
	void LiveKit을_확인할_수_없으면_종료하지_않는다() throws Exception {
		Broadcast broadcast = staleLive();
		liveKit.failHasParticipant = true;

		liveService.reconcileLive();
		Thread.sleep(500);

		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.LIVE);
		assertThat(liveKit.deletedRooms).isEmpty();
	}

	@Test
	void 방_닫기가_실패해도_종료는_유지된다() throws Exception {
		Broadcast broadcast = staleLive();
		liveKit.failDelete = true;

		liveService.reconcileLive();

		awaitStatus(broadcast, BroadcastStatus.ENDED);
	}

	@Test
	void READY와_ENDED_방송은_건드리지_않는다() throws Exception {
		Broadcast ready = broadcastRepository.saveAndFlush(new Broadcast(owner, "준비", null));
		Broadcast ended = staleLive();
		jdbc.update("update broadcasts set status = 'ENDED', ended_at = now() where id = ?", ended.getId());

		liveService.reconcileLive();
		Thread.sleep(500);

		assertThat(statusOf(ready)).isEqualTo(BroadcastStatus.READY);
		assertThat(statusOf(ended)).isEqualTo(BroadcastStatus.ENDED);
		assertThat(liveKit.deletedRooms).doesNotContain(RoomNames.of(ready.getId()), RoomNames.of(ended.getId()));
	}
}
