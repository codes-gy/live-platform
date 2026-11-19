package com.gy.live.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gy.live.broadcast.BroadcastLiveService;
import com.gy.live.support.AuthSupport;
import com.gy.live.support.FakeLiveKitRoomClient;
import com.gy.live.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class CommentControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeLiveKitRoomClient liveKit;

	@Autowired
	CommentStreams commentStreams;

	@Autowired
	BroadcastLiveService liveService;

	Cookie owner;
	Cookie viewer;
	Cookie other;
	long viewerId;

	@BeforeEach
	void setUp() throws Exception {
		liveKit.reset();
		owner = AuthSupport.signupNewUser(mockMvc, "방송주인");
		viewer = AuthSupport.signupNewUser(mockMvc, "시청자1");
		other = AuthSupport.signupNewUser(mockMvc, "다른사람");
		String me = mockMvc.perform(get("/api/auth/me").cookie(viewer)).andReturn().getResponse().getContentAsString();
		viewerId = ((Number) JsonPath.read(me, "$.id")).longValue();
	}

	private long readyBroadcast() throws Exception {
		String body = mockMvc.perform(post("/api/broadcasts").cookie(owner)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"댓글 방송"}
								"""))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private long liveBroadcast() throws Exception {
		long id = readyBroadcast();
		mockMvc.perform(post("/api/broadcasts/{id}/start", id).cookie(owner)).andExpect(status().isOk());
		return id;
	}

	private long endedBroadcast() throws Exception {
		long id = liveBroadcast();
		endByOwner(id);
		return id;
	}

	private void endByOwner(long id) throws Exception {
		mockMvc.perform(post("/api/broadcasts/{id}/end", id).cookie(owner)).andExpect(status().isOk());
	}

	private ResultActions write(long broadcastId, Cookie cookie, String content) throws Exception {
		var request = post("/api/broadcasts/{id}/comments", broadcastId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"%s\"}".formatted(content));
		return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
	}

	private long writeOk(long broadcastId, Cookie cookie, String content) throws Exception {
		String body = write(broadcastId, cookie, content).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions edit(long broadcastId, long commentId, Cookie cookie, String content) throws Exception {
		var request = patch("/api/broadcasts/{id}/comments/{commentId}", broadcastId, commentId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"%s\"}".formatted(content));
		return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
	}

	private ResultActions remove(long broadcastId, long commentId, Cookie cookie) throws Exception {
		var request = delete("/api/broadcasts/{id}/comments/{commentId}", broadcastId, commentId);
		return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
	}

	private MvcResult subscribe(long broadcastId) throws Exception {
		return mockMvc.perform(get("/api/broadcasts/{id}/comments/stream", broadcastId)
						.accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(request().asyncStarted())
				.andExpect(header().string("X-Accel-Buffering", "no"))
				.andReturn();
	}

	private static String received(MvcResult stream) throws Exception {
		return stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	@Nested
	@DisplayName("댓글 작성")
	class Create {

		@Test
		void 로그인_사용자는_방송_중에_쓴다_앞뒤_공백은_지운다() throws Exception {
			long id = liveBroadcast();

			write(id, viewer, "  안녕하세요  ")
					.andExpect(status().isCreated())
					.andExpect(header().string("Location",
							startsWith("/api/broadcasts/" + id + "/comments/")))
					.andExpect(jsonPath("$.id").isNumber())
					.andExpect(jsonPath("$.broadcastId").value(id))
					.andExpect(jsonPath("$.author.id").value(viewerId))
					.andExpect(jsonPath("$.author.nickname").value("시청자1"))
					.andExpect(jsonPath("$.content").value("안녕하세요"))
					.andExpect(jsonPath("$.createdAt").exists())
					.andExpect(jsonPath("$.updatedAt").value(nullValue()));
		}

		@Test
		void 비로그인은_401() throws Exception {
			long id = liveBroadcast();

			write(id, null, "안녕").andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		}

		@Test
		void 시작_전_방송은_409() throws Exception {
			long id = readyBroadcast();

			write(id, viewer, "안녕").andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_NOT_LIVE"));
		}

		@Test
		void 종료된_방송은_409() throws Exception {
			long id = endedBroadcast();

			write(id, viewer, "안녕").andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("BROADCAST_ENDED"));
		}

		@Test
		void 없는_방송은_404() throws Exception {
			write(Long.MAX_VALUE, viewer, "안녕").andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("BROADCAST_NOT_FOUND"));
		}

		@Test
		void 빈_내용과_500자_초과는_400() throws Exception {
			long id = liveBroadcast();

			write(id, viewer, "   ").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
					.andExpect(jsonPath("$.message").value("댓글 내용을 입력해 주세요."));
			write(id, viewer, "가".repeat(501)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("댓글은 500자 이하로 입력해 주세요."));
			write(id, viewer, "가".repeat(500)).andExpect(status().isCreated());
		}
	}

	@Nested
	@DisplayName("댓글 목록")
	class ListComments {

		@Test
		void 비로그인도_최신순으로_커서를_따라_끝까지_읽는다() throws Exception {
			long id = liveBroadcast();
			long[] ids = new long[5];
			for (int i = 0; i < 5; i++) {
				ids[i] = writeOk(id, viewer, "댓글" + i);
			}

			mockMvc.perform(get("/api/broadcasts/{id}/comments", id).param("size", "2"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items[*].content").value(contains("댓글4", "댓글3")))
					.andExpect(jsonPath("$.hasNext").value(true))
					.andExpect(jsonPath("$.nextBefore").value(ids[3]));
			mockMvc.perform(get("/api/broadcasts/{id}/comments", id).param("size", "2")
							.param("before", String.valueOf(ids[3])))
					.andExpect(jsonPath("$.items[*].content").value(contains("댓글2", "댓글1")))
					.andExpect(jsonPath("$.nextBefore").value(ids[1]));
			mockMvc.perform(get("/api/broadcasts/{id}/comments", id).param("size", "2")
							.param("before", String.valueOf(ids[1])))
					.andExpect(jsonPath("$.items[*].content").value(contains("댓글0")))
					.andExpect(jsonPath("$.hasNext").value(false))
					.andExpect(jsonPath("$.nextBefore").value(nullValue()));
		}

		@Test
		void 다른_방송의_댓글은_섞이지_않는다() throws Exception {
			long mine = liveBroadcast();
			long another = liveBroadcast();
			writeOk(mine, viewer, "여기");
			writeOk(another, viewer, "저기");

			mockMvc.perform(get("/api/broadcasts/{id}/comments", mine))
					.andExpect(jsonPath("$.items[*].content").value(contains("여기")));
		}

		@Test
		void 종료된_방송도_다시보기용으로_읽는다() throws Exception {
			long id = liveBroadcast();
			writeOk(id, viewer, "방송 중 댓글");
			endByOwner(id);

			mockMvc.perform(get("/api/broadcasts/{id}/comments", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items", hasSize(1)));
		}

		@Test
		void 페이지_크기는_1에서_100_사이로_맞춘다() throws Exception {
			long id = liveBroadcast();
			writeOk(id, viewer, "하나");
			writeOk(id, viewer, "둘");

			mockMvc.perform(get("/api/broadcasts/{id}/comments", id).param("size", "0"))
					.andExpect(jsonPath("$.items", hasSize(1)))
					.andExpect(jsonPath("$.hasNext").value(true));
			mockMvc.perform(get("/api/broadcasts/{id}/comments", id).param("size", "1000"))
					.andExpect(jsonPath("$.items", hasSize(2)));
		}

		@Test
		void 없는_방송은_404_잘못된_커서는_400() throws Exception {
			mockMvc.perform(get("/api/broadcasts/{id}/comments", Long.MAX_VALUE))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("BROADCAST_NOT_FOUND"));
			long id = liveBroadcast();
			mockMvc.perform(get("/api/broadcasts/{id}/comments", id).param("before", "abc"))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("댓글 수정 · 삭제")
	class EditAndDelete {

		@Test
		void 작성자는_수정한다() throws Exception {
			long id = liveBroadcast();
			long commentId = writeOk(id, viewer, "오타");

			edit(id, commentId, viewer, " 고침 ")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content").value("고침"))
					.andExpect(jsonPath("$.updatedAt").exists());
		}

		@Test
		void 작성자가_아니면_방송_소유자도_수정할_수_없다() throws Exception {
			long id = liveBroadcast();
			long commentId = writeOk(id, viewer, "원문");

			edit(id, commentId, other, "바꿈").andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("NOT_COMMENT_AUTHOR"));
			edit(id, commentId, owner, "바꿈").andExpect(status().isForbidden());
			edit(id, commentId, null, "바꿈").andExpect(status().isUnauthorized());

			mockMvc.perform(get("/api/broadcasts/{id}/comments", id))
					.andExpect(jsonPath("$.items[0].content").value("원문"));
		}

		@Test
		void 작성자는_삭제한다() throws Exception {
			long id = liveBroadcast();
			long commentId = writeOk(id, viewer, "지울 댓글");

			remove(id, commentId, viewer).andExpect(status().isNoContent());

			mockMvc.perform(get("/api/broadcasts/{id}/comments", id)).andExpect(jsonPath("$.items", hasSize(0)));
		}

		@Test
		void 방송_소유자는_남의_댓글도_지운다_종료_뒤에도() throws Exception {
			long id = liveBroadcast();
			long commentId = writeOk(id, viewer, "부적절한 댓글");
			endByOwner(id);

			remove(id, commentId, owner).andExpect(status().isNoContent());
		}

		@Test
		void 다른_사용자와_비로그인은_삭제할_수_없다() throws Exception {
			long id = liveBroadcast();
			long commentId = writeOk(id, viewer, "남길 댓글");

			remove(id, commentId, other).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("NOT_COMMENT_AUTHOR"));
			remove(id, commentId, null).andExpect(status().isUnauthorized());

			mockMvc.perform(get("/api/broadcasts/{id}/comments", id)).andExpect(jsonPath("$.items", hasSize(1)));
		}

		@Test
		void 다른_방송_경로로는_찾을_수_없다() throws Exception {
			long id = liveBroadcast();
			long another = liveBroadcast();
			long commentId = writeOk(id, viewer, "댓글");

			edit(another, commentId, viewer, "바꿈").andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
			remove(another, commentId, viewer).andExpect(status().isNotFound());
		}

		@Test
		void 댓글이_있는_방송도_삭제된다() throws Exception {
			long id = liveBroadcast();
			writeOk(id, viewer, "댓글");
			endByOwner(id);

			mockMvc.perform(delete("/api/broadcasts/{id}", id).cookie(owner)).andExpect(status().isNoContent());
			mockMvc.perform(get("/api/broadcasts/{id}/comments", id)).andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("실시간 구독(SSE)")
	class Stream {

		@Test
		void 비로그인도_구독하고_커밋된_새_댓글을_받는다() throws Exception {
			long id = liveBroadcast();
			MvcResult stream = subscribe(id);
			assertThat(stream.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);

			long commentId = writeOk(id, viewer, "실시간 댓글");

			assertThat(received(stream))
					.contains("event:comment\n")
					.contains("\"id\":" + commentId)
					.contains("\"content\":\"실시간 댓글\"")
					.contains("\"nickname\":\"시청자1\"");
		}

		@Test
		void 다른_방송의_댓글과_실패한_작성은_받지_않는다() throws Exception {
			long id = liveBroadcast();
			long another = liveBroadcast();
			MvcResult stream = subscribe(id);

			writeOk(another, viewer, "다른 방송");
			write(id, viewer, "가".repeat(501)).andExpect(status().isBadRequest());

			assertThat(received(stream)).doesNotContain("event:comment");
		}

		@Test
		void 시작_전_방송에_쓰려다_막힌_댓글은_보내지_않는다() throws Exception {
			long id = readyBroadcast();
			MvcResult stream = subscribe(id);

			write(id, viewer, "아직").andExpect(status().isConflict());

			assertThat(received(stream)).doesNotContain("event:comment");
		}

		@Test
		void 수정과_삭제도_받는다() throws Exception {
			long id = liveBroadcast();
			long commentId = writeOk(id, viewer, "원문");
			MvcResult stream = subscribe(id);

			edit(id, commentId, viewer, "수정본").andExpect(status().isOk());
			remove(id, commentId, viewer).andExpect(status().isNoContent());

			assertThat(received(stream))
					.contains("event:comment-updated\n")
					.contains("\"content\":\"수정본\"")
					.contains("event:comment-deleted\ndata:{\"id\":" + commentId + "}");
		}

		@Test
		void 소유자가_종료하면_ended를_보내고_연결을_닫는다() throws Exception {
			long id = liveBroadcast();
			MvcResult stream = subscribe(id);
			assertThat(commentStreams.subscriberCount(id)).isEqualTo(1);

			endByOwner(id);

			assertThat(received(stream)).contains("event:ended\ndata:{\"broadcastId\":" + id + "}");
			mockMvc.perform(asyncDispatch(stream))
					.andExpect(status().isOk());
			assertThat(commentStreams.subscriberCount(id)).isZero();
		}

		@Test
		void 방이_닫혀_자동_종료돼도_ended를_보낸다() throws Exception {
			long id = liveBroadcast();
			MvcResult stream = subscribe(id);

			liveService.onRoomFinished("broadcast-" + id);

			assertThat(received(stream)).contains("event:ended");
			assertThat(commentStreams.subscriberCount(id)).isZero();
		}

		@Test
		void 이미_종료된_방송을_구독하면_바로_ended를_받는다() throws Exception {
			long id = endedBroadcast();

			MvcResult stream = subscribe(id);

			assertThat(received(stream)).contains("event:ended");
			assertThat(commentStreams.subscriberCount(id)).isZero();
		}

		@Test
		void 연결_유지용_주석을_주기적으로_보낸다() throws Exception {
			long id = liveBroadcast();
			MvcResult stream = subscribe(id);

			commentStreams.sendHeartbeat();

			assertThat(received(stream)).contains(":ping\n");
		}

		@Test
		void 없는_방송은_404() throws Exception {
			mockMvc.perform(get("/api/broadcasts/{id}/comments/stream", Long.MAX_VALUE)
							.accept(MediaType.TEXT_EVENT_STREAM))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("BROADCAST_NOT_FOUND"));
			assertThat(commentStreams.subscriberCount(Long.MAX_VALUE)).isZero();
		}
	}
}
