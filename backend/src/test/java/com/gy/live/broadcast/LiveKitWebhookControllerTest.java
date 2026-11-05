package com.gy.live.broadcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gy.live.support.FakeLiveKitRoomClient;
import com.gy.live.support.IntegrationTest;
import com.gy.live.user.User;
import com.gy.live.user.UserRepository;
import io.livekit.server.AccessToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class LiveKitWebhookControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BroadcastRepository broadcastRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	FakeLiveKitRoomClient liveKit;

	User owner;

	@BeforeEach
	void setUp() {
		liveKit.reset();
		owner = userRepository.save(new User(UUID.randomUUID() + "@example.com", "hash", "방송주인"));
	}

	private Broadcast liveBroadcast() {
		Broadcast broadcast = new Broadcast(owner, "라이브", null);
		broadcast.start();
		return broadcastRepository.saveAndFlush(broadcast);
	}

	private BroadcastStatus statusOf(Broadcast broadcast) {
		return broadcastRepository.findById(broadcast.getId()).orElseThrow().getStatus();
	}

	/** LiveKit과 같은 방식으로 서명한다: 본문 SHA-256을 담은 JWT. */
	private static String sign(String body, String secret) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
		AccessToken token = new AccessToken(IntegrationTest.LIVEKIT_KEY, secret);
		token.setSha256(Base64.getEncoder().encodeToString(hash));
		return token.toJwt();
	}

	private ResultActions send(String body, String authorization) throws Exception {
		var request = post("/api/livekit/webhook").contentType("application/webhook+json").content(body);
		return mockMvc.perform(authorization == null ? request : request.header(HttpHeaders.AUTHORIZATION, authorization));
	}

	private static String roomFinished(Broadcast broadcast) {
		return """
				{"event":"room_finished","room":{"name":"broadcast-%d"}}
				""".formatted(broadcast.getId());
	}

	private static String participantLeft(Broadcast broadcast, String identity) {
		return """
				{"event":"participant_left","room":{"name":"broadcast-%d"},"participant":{"identity":"%s"}}
				""".formatted(broadcast.getId(), identity);
	}

	private void awaitStatus(Broadcast broadcast, BroadcastStatus expected) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 3_000;
		while (statusOf(broadcast) != expected && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
		assertThat(statusOf(broadcast)).isEqualTo(expected);
	}

	@Test
	void room_finished가_오면_방송_중인_방송을_종료한다() throws Exception {
		Broadcast broadcast = liveBroadcast();
		String body = roomFinished(broadcast);

		send(body, sign(body, IntegrationTest.LIVEKIT_SECRET)).andExpect(status().isOk());

		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.ENDED);
	}

	@Test
	void 같은_웹훅이_다시_와도_결과가_같다() throws Exception {
		Broadcast broadcast = liveBroadcast();
		String body = roomFinished(broadcast);
		String signature = sign(body, IntegrationTest.LIVEKIT_SECRET);

		send(body, signature).andExpect(status().isOk());
		send(body, signature).andExpect(status().isOk());

		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.ENDED);
	}

	@Test
	void 다른_비밀키로_서명한_웹훅은_401이고_방송은_계속된다() throws Exception {
		Broadcast broadcast = liveBroadcast();
		String body = roomFinished(broadcast);

		send(body, sign(body, "attacker-secret-attacker-secret-0123456789"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_WEBHOOK"));

		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.LIVE);
	}

	@Test
	void 서명한_본문과_다른_본문이면_401() throws Exception {
		Broadcast broadcast = liveBroadcast();
		String signed = """
				{"event":"room_started","room":{"name":"broadcast-%d"}}
				""".formatted(broadcast.getId());

		send(roomFinished(broadcast), sign(signed, IntegrationTest.LIVEKIT_SECRET))
				.andExpect(status().isUnauthorized());

		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.LIVE);
	}

	@Test
	void 서명이_없으면_401() throws Exception {
		Broadcast broadcast = liveBroadcast();

		send(roomFinished(broadcast), null).andExpect(status().isUnauthorized());

		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.LIVE);
	}

	@Test
	void 방송자가_나가고_돌아오지_않으면_유예_시간_뒤_종료하고_방을_닫는다() throws Exception {
		Broadcast broadcast = liveBroadcast();
		String body = participantLeft(broadcast, "broadcaster-" + owner.getId());

		send(body, sign(body, IntegrationTest.LIVEKIT_SECRET)).andExpect(status().isOk());

		awaitStatus(broadcast, BroadcastStatus.ENDED);
		assertThat(liveKit.deletedRooms).contains("broadcast-" + broadcast.getId());
	}

	@Test
	void 방송자가_유예_시간_안에_돌아오면_계속된다() throws Exception {
		Broadcast broadcast = liveBroadcast();
		String identity = "broadcaster-" + owner.getId();
		liveKit.join("broadcast-" + broadcast.getId(), identity);
		String body = participantLeft(broadcast, identity);

		send(body, sign(body, IntegrationTest.LIVEKIT_SECRET)).andExpect(status().isOk());

		Thread.sleep(600);
		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.LIVE);
	}

	@Test
	void 시청자가_나가는_것은_무시한다() throws Exception {
		Broadcast broadcast = liveBroadcast();
		String body = participantLeft(broadcast, "viewer-abc");

		send(body, sign(body, IntegrationTest.LIVEKIT_SECRET)).andExpect(status().isOk());

		Thread.sleep(600);
		assertThat(statusOf(broadcast)).isEqualTo(BroadcastStatus.LIVE);
	}

	@Test
	void 이_서비스가_만들지_않은_방은_무시한다() throws Exception {
		String body = """
				{"event":"room_finished","room":{"name":"someone-elses-room"}}
				""";

		send(body, sign(body, IntegrationTest.LIVEKIT_SECRET)).andExpect(status().isOk());
	}
}
