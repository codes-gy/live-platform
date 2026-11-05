package com.gy.live.broadcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gy.live.support.AuthSupport;
import com.gy.live.support.FakeLiveKitRoomClient;
import com.gy.live.support.IntegrationTest;
import com.gy.live.support.JwtClaims;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class BroadcastLiveControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BroadcastRepository broadcastRepository;

	@Autowired
	FakeLiveKitRoomClient liveKit;

	Cookie owner;
	Cookie other;
	long ownerId;

	@BeforeEach
	void setUp() throws Exception {
		liveKit.reset();
		owner = AuthSupport.signupNewUser(mockMvc, "방송주인");
		other = AuthSupport.signupNewUser(mockMvc, "다른사람");
		String me = mockMvc.perform(get("/api/auth/me").cookie(owner)).andReturn().getResponse().getContentAsString();
		ownerId = ((Number) JsonPath.read(me, "$.id")).longValue();
	}

	private long createBroadcast() throws Exception {
		String body = mockMvc.perform(post("/api/broadcasts").cookie(owner)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"라이브","description":null}
								"""))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private long liveBroadcast() throws Exception {
		long id = createBroadcast();
		start(id, owner).andExpect(status().isOk());
		return id;
	}

	private ResultActions start(long id, Cookie cookie) throws Exception {
		var request = post("/api/broadcasts/{id}/start", id);
		return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
	}

	private ResultActions end(long id, Cookie cookie) throws Exception {
		var request = post("/api/broadcasts/{id}/end", id);
		return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
	}

	private BroadcastStatus statusOf(long id) {
		return broadcastRepository.findById(id).orElseThrow().getStatus();
	}

	private static String tokenOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
	}

	@Nested
	@DisplayName("방송 시작")
	class Start {

		@Test
		void 소유자가_시작하면_LIVE가_되고_송출_토큰을_받는다() throws Exception {
			long id = createBroadcast();

			ResultActions result = start(id, owner)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.serverUrl").value("wss://test.livekit.cloud"))
					.andExpect(jsonPath("$.roomName").value("broadcast-" + id))
					.andExpect(jsonPath("$.identity").value("broadcaster-" + ownerId))
					.andExpect(jsonPath("$.broadcast.status").value("LIVE"))
					.andExpect(jsonPath("$.broadcast.startedAt").exists());

			String token = tokenOf(result);
			assertThat(JwtClaims.<String>read(token, "$.video.room")).isEqualTo("broadcast-" + id);
			assertThat(JwtClaims.<Boolean>read(token, "$.video.roomJoin")).isTrue();
			assertThat(JwtClaims.<Boolean>read(token, "$.video.canPublish")).isTrue();
			assertThat(JwtClaims.<List<String>>read(token, "$.video.canPublishSources"))
					.containsExactlyInAnyOrder("camera", "microphone", "screen_share", "screen_share_audio");
			assertThat(JwtClaims.<String>read(token, "$.iss")).isEqualTo(IntegrationTest.LIVEKIT_KEY);
			// 토큰 유효 시간 1시간(설정값을 밀리초로 넘기는지 확인)
			long ttl = JwtClaims.<Integer>read(token, "$.exp") - System.currentTimeMillis() / 1000;
			assertThat(ttl).isBetween(3500L, 3600L);

			assertThat(liveKit.createdRooms).containsExactly("broadcast-" + id);
			assertThat(statusOf(id)).isEqualTo(BroadcastStatus.LIVE);
		}

		@Test
		void 다른_사용자는_403이고_방도_만들지_않는다() throws Exception {
			long id = createBroadcast();

			start(id, other)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("NOT_BROADCAST_OWNER"));

			assertThat(liveKit.createdRooms).isEmpty();
			assertThat(statusOf(id)).isEqualTo(BroadcastStatus.READY);
		}

		@Test
		void 비로그인은_401() throws Exception {
			long id = createBroadcast();

			start(id, null).andExpect(status().isUnauthorized());

			assertThat(statusOf(id)).isEqualTo(BroadcastStatus.READY);
		}

		@Test
		void 이미_방송_중이면_409() throws Exception {
			long id = liveBroadcast();

			start(id, owner)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_ALREADY_LIVE"));
		}

		@Test
		void 종료된_방송은_다시_시작할_수_없다() throws Exception {
			long id = liveBroadcast();
			end(id, owner).andExpect(status().isOk());

			start(id, owner)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_ENDED"));
		}

		@Test
		void LiveKit_방_만들기에_실패하면_502이고_READY로_남는다() throws Exception {
			long id = createBroadcast();
			liveKit.failCreate = true;

			start(id, owner)
					.andExpect(status().isBadGateway())
					.andExpect(jsonPath("$.code").value("LIVEKIT_UNAVAILABLE"));

			assertThat(statusOf(id)).isEqualTo(BroadcastStatus.READY);
		}
	}

	@Nested
	@DisplayName("송출 토큰 재발급")
	class PublisherToken {

		@Test
		void 소유자는_방송_중에_다시_받는다() throws Exception {
			long id = liveBroadcast();

			mockMvc.perform(post("/api/broadcasts/{id}/publisher-token", id).cookie(owner))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.identity").value("broadcaster-" + ownerId));
		}

		@Test
		void 다른_사용자는_403() throws Exception {
			long id = liveBroadcast();

			mockMvc.perform(post("/api/broadcasts/{id}/publisher-token", id).cookie(other))
					.andExpect(status().isForbidden());
		}

		@Test
		void 시작_전이면_409() throws Exception {
			long id = createBroadcast();

			mockMvc.perform(post("/api/broadcasts/{id}/publisher-token", id).cookie(owner))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_NOT_LIVE"));
		}
	}

	@Nested
	@DisplayName("시청 토큰")
	class ViewerToken {

		@Test
		void 비로그인도_방송_중이면_받기_전용_토큰을_받는다() throws Exception {
			long id = liveBroadcast();

			ResultActions result = mockMvc.perform(post("/api/broadcasts/{id}/viewer-token", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.roomName").value("broadcast-" + id))
					.andExpect(jsonPath("$.broadcast.status").value("LIVE"));

			String token = tokenOf(result);
			assertThat(JwtClaims.<Boolean>read(token, "$.video.canPublish")).isFalse();
			assertThat(JwtClaims.<Boolean>read(token, "$.video.canSubscribe")).isTrue();
			assertThat(JwtClaims.<Boolean>read(token, "$.video.canPublishData")).isFalse();
			assertThat(JwtClaims.<Boolean>read(token, "$.video.hidden")).isTrue();
			assertThat(JwtClaims.<String>read(token, "$.name")).isEqualTo("시청자");
			assertThat(JwtClaims.<String>read(token, "$.sub")).startsWith("viewer-");
		}

		@Test
		void 로그인한_시청자는_닉네임이_이름이_된다() throws Exception {
			long id = liveBroadcast();

			ResultActions result = mockMvc.perform(post("/api/broadcasts/{id}/viewer-token", id).cookie(other))
					.andExpect(status().isOk());

			assertThat(JwtClaims.<String>read(tokenOf(result), "$.name")).isEqualTo("다른사람");
		}

		@Test
		void 시작_전이면_409() throws Exception {
			long id = createBroadcast();

			mockMvc.perform(post("/api/broadcasts/{id}/viewer-token", id))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_NOT_LIVE"));
		}

		@Test
		void 종료된_방송이면_409() throws Exception {
			long id = liveBroadcast();
			end(id, owner);

			mockMvc.perform(post("/api/broadcasts/{id}/viewer-token", id))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_ENDED"));
		}

		@Test
		void 없는_방송은_404() throws Exception {
			mockMvc.perform(post("/api/broadcasts/{id}/viewer-token", Long.MAX_VALUE))
					.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("방송 종료")
	class End {

		@Test
		void 소유자가_종료하면_ENDED가_되고_방을_닫는다() throws Exception {
			long id = liveBroadcast();

			end(id, owner)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("ENDED"))
					.andExpect(jsonPath("$.endedAt").exists());

			assertThat(liveKit.deletedRooms).containsExactly("broadcast-" + id);
			assertThat(statusOf(id)).isEqualTo(BroadcastStatus.ENDED);
		}

		@Test
		void 다른_사용자는_403이고_방송은_계속된다() throws Exception {
			long id = liveBroadcast();

			end(id, other).andExpect(status().isForbidden());

			assertThat(liveKit.deletedRooms).isEmpty();
			assertThat(statusOf(id)).isEqualTo(BroadcastStatus.LIVE);
		}

		@Test
		void 비로그인은_401() throws Exception {
			long id = liveBroadcast();

			end(id, null).andExpect(status().isUnauthorized());

			assertThat(statusOf(id)).isEqualTo(BroadcastStatus.LIVE);
		}

		@Test
		void 두_번_종료해도_200() throws Exception {
			long id = liveBroadcast();
			end(id, owner);

			end(id, owner)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("ENDED"));
		}

		@Test
		void 시작_전_방송은_409() throws Exception {
			long id = createBroadcast();

			end(id, owner)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_NOT_LIVE"));
		}

		@Test
		void 방_닫기에_실패해도_방송은_종료된다() throws Exception {
			long id = liveBroadcast();
			liveKit.failDelete = true;

			end(id, owner)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("ENDED"));
		}

		@Test
		void 종료한_뒤에는_송출_토큰을_받을_수_없다() throws Exception {
			long id = liveBroadcast();
			end(id, owner);

			mockMvc.perform(post("/api/broadcasts/{id}/publisher-token", id).cookie(owner))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_ENDED"));
		}
	}
}
