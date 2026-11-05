package com.gy.live.broadcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gy.live.support.AuthSupport;
import com.gy.live.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class BroadcastControllerTest {

	private static final String BODY = """
			{"title":"%s","description":"%s"}
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BroadcastRepository broadcastRepository;

	Cookie owner;
	Cookie other;
	long ownerId;

	@BeforeEach
	void setUp() throws Exception {
		owner = AuthSupport.signupNewUser(mockMvc, "방송주인");
		other = AuthSupport.signupNewUser(mockMvc, "다른사람");
		String me = mockMvc.perform(get("/api/auth/me").cookie(owner)).andReturn().getResponse().getContentAsString();
		ownerId = ((Number) JsonPath.read(me, "$.id")).longValue();
	}

	private long createBroadcast(Cookie cookie, String title) throws Exception {
		String body = mockMvc.perform(post("/api/broadcasts").cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY.formatted(title, "설명")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private void makeLive(long broadcastId) {
		Broadcast broadcast = broadcastRepository.findById(broadcastId).orElseThrow();
		broadcast.start();
		broadcastRepository.saveAndFlush(broadcast);
	}

	@Nested
	@DisplayName("등록")
	class Create {

		@Test
		void 로그인한_사용자가_등록하면_READY_상태로_만들어지고_소유자가_된다() throws Exception {
			mockMvc.perform(post("/api/broadcasts").cookie(owner)
							.contentType(MediaType.APPLICATION_JSON)
							.content(BODY.formatted("  첫 방송  ", "   ")))
					.andExpect(status().isCreated())
					.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/broadcasts/")))
					.andExpect(jsonPath("$.title").value("첫 방송"))
					.andExpect(jsonPath("$.description").doesNotExist())
					.andExpect(jsonPath("$.status").value("READY"))
					.andExpect(jsonPath("$.owner.id").value(ownerId))
					.andExpect(jsonPath("$.owner.nickname").value("방송주인"));
		}

		@Test
		void 비로그인이면_401() throws Exception {
			mockMvc.perform(post("/api/broadcasts")
							.contentType(MediaType.APPLICATION_JSON)
							.content(BODY.formatted("제목", "설명")))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		}

		@Test
		void 제목이_비면_400() throws Exception {
			mockMvc.perform(post("/api/broadcasts").cookie(owner)
							.contentType(MediaType.APPLICATION_JSON)
							.content(BODY.formatted(" ", "설명")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("방송 제목을 입력해 주세요."));
		}

		@Test
		void 제목이_100자를_넘으면_400() throws Exception {
			mockMvc.perform(post("/api/broadcasts").cookie(owner)
							.contentType(MediaType.APPLICATION_JSON)
							.content(BODY.formatted("가".repeat(101), "설명")))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("조회 (로그인 없이)")
	class Read {

		@Test
		void 상세를_비로그인으로_조회한다() throws Exception {
			long id = createBroadcast(owner, "상세 방송");

			mockMvc.perform(get("/api/broadcasts/{id}", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(id))
					.andExpect(jsonPath("$.title").value("상세 방송"));
		}

		@Test
		void 없는_방송은_404() throws Exception {
			mockMvc.perform(get("/api/broadcasts/{id}", Long.MAX_VALUE))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("BROADCAST_NOT_FOUND"));
		}

		@Test
		void 목록은_최신순이고_소유자와_상태로_거른다() throws Exception {
			long first = createBroadcast(owner, "먼저");
			long second = createBroadcast(owner, "나중");
			makeLive(second);
			createBroadcast(other, "남의 방송");

			mockMvc.perform(get("/api/broadcasts").param("ownerId", String.valueOf(ownerId)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items", hasSize(2)))
					.andExpect(jsonPath("$.items[0].id").value(second))
					.andExpect(jsonPath("$.items[1].id").value(first))
					.andExpect(jsonPath("$.totalElements").value(2));

			mockMvc.perform(get("/api/broadcasts")
							.param("ownerId", String.valueOf(ownerId))
							.param("status", "LIVE"))
					.andExpect(jsonPath("$.items", hasSize(1)))
					.andExpect(jsonPath("$.items[0].id").value(second))
					.andExpect(jsonPath("$.items[0].status").value("LIVE"))
					.andExpect(jsonPath("$.items[0].startedAt").exists());
		}

		@Test
		void 페이지_크기는_최대_50() throws Exception {
			mockMvc.perform(get("/api/broadcasts").param("size", "1000"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.size").value(BroadcastService.MAX_PAGE_SIZE));
		}

		@Test
		void 없는_상태값이면_400() throws Exception {
			mockMvc.perform(get("/api/broadcasts").param("status", "PAUSED"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		}
	}

	@Nested
	@DisplayName("수정")
	class Update {

		@Test
		void 소유자는_수정할_수_있다() throws Exception {
			long id = createBroadcast(owner, "원래 제목");

			mockMvc.perform(patch("/api/broadcasts/{id}", id).cookie(owner)
							.contentType(MediaType.APPLICATION_JSON)
							.content(BODY.formatted("바뀐 제목", "바뀐 설명")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.title").value("바뀐 제목"))
					.andExpect(jsonPath("$.description").value("바뀐 설명"));

			assertThat(broadcastRepository.findById(id).orElseThrow().getTitle()).isEqualTo("바뀐 제목");
		}

		@Test
		void 다른_사용자는_403이고_바뀌지_않는다() throws Exception {
			long id = createBroadcast(owner, "원래 제목");

			mockMvc.perform(patch("/api/broadcasts/{id}", id).cookie(other)
							.contentType(MediaType.APPLICATION_JSON)
							.content(BODY.formatted("가로채기", "설명")))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("NOT_BROADCAST_OWNER"));

			assertThat(broadcastRepository.findById(id).orElseThrow().getTitle()).isEqualTo("원래 제목");
		}

		@Test
		void 비로그인은_401() throws Exception {
			long id = createBroadcast(owner, "원래 제목");

			mockMvc.perform(patch("/api/broadcasts/{id}", id)
							.contentType(MediaType.APPLICATION_JSON)
							.content(BODY.formatted("가로채기", "설명")))
					.andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@DisplayName("삭제")
	class Delete {

		@Test
		void 소유자는_삭제할_수_있다() throws Exception {
			long id = createBroadcast(owner, "지울 방송");

			mockMvc.perform(delete("/api/broadcasts/{id}", id).cookie(owner))
					.andExpect(status().isNoContent());

			mockMvc.perform(get("/api/broadcasts/{id}", id)).andExpect(status().isNotFound());
		}

		@Test
		void 다른_사용자는_403이고_남아_있다() throws Exception {
			long id = createBroadcast(owner, "지울 방송");

			mockMvc.perform(delete("/api/broadcasts/{id}", id).cookie(other))
					.andExpect(status().isForbidden());

			assertThat(broadcastRepository.existsById(id)).isTrue();
		}

		@Test
		void 비로그인은_401() throws Exception {
			long id = createBroadcast(owner, "지울 방송");

			mockMvc.perform(delete("/api/broadcasts/{id}", id))
					.andExpect(status().isUnauthorized());

			assertThat(broadcastRepository.existsById(id)).isTrue();
		}

		@Test
		void 방송_중에는_409() throws Exception {
			long id = createBroadcast(owner, "방송 중");
			makeLive(id);

			mockMvc.perform(delete("/api/broadcasts/{id}", id).cookie(owner))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_LIVE_CANNOT_DELETE"));
		}

		@Test
		void 없는_방송은_404() throws Exception {
			mockMvc.perform(delete("/api/broadcasts/{id}", Long.MAX_VALUE).cookie(owner))
					.andExpect(status().isNotFound());
		}
	}
}
