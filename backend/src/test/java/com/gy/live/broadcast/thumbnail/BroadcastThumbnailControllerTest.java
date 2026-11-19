package com.gy.live.broadcast.thumbnail;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.broadcast.BroadcastRepository;
import com.gy.live.support.AuthSupport;
import com.gy.live.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class BroadcastThumbnailControllerTest {

	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BroadcastRepository broadcastRepository;

	Cookie owner;
	Cookie other;
	long broadcastId;

	@BeforeEach
	void setUp() throws Exception {
		owner = AuthSupport.signupNewUser(mockMvc, "방송주인");
		other = AuthSupport.signupNewUser(mockMvc, "다른사람");
		String body = mockMvc.perform(post("/api/broadcasts").cookie(owner).contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"썸네일 방송\"}"))
				.andReturn().getResponse().getContentAsString();
		broadcastId = ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private void setLive(boolean end) {
		Broadcast broadcast = broadcastRepository.findById(broadcastId).orElseThrow();
		broadcast.start();
		if (end) broadcast.end();
		broadcastRepository.saveAndFlush(broadcast);
	}

	private String url() {
		return "/api/broadcasts/" + broadcastId + "/thumbnail";
	}

	@Test
	void 방송_중_소유자가_올리면_비로그인도_받아_볼_수_있고_다시_올리면_바뀐다() throws Exception {
		setLive(false);
		mockMvc.perform(put(url()).cookie(owner).contentType(MediaType.IMAGE_JPEG).content(JPEG))
				.andExpect(status().isNoContent());
		byte[] next = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9, 9};
		mockMvc.perform(put(url()).cookie(owner).contentType(MediaType.IMAGE_JPEG).content(next))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(url()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_JPEG))
				.andExpect(content().bytes(next));
		// 목록 · 상세 응답이 썸네일 갱신 시각을 실어, 카드가 없는 썸네일을 요청하지 않게 한다
		mockMvc.perform(get("/api/broadcasts/" + broadcastId))
				.andExpect(jsonPath("$.thumbnailUpdatedAt").isNotEmpty());
	}

	@Test
	void 썸네일이_없으면_응답의_갱신_시각은_null() throws Exception {
		mockMvc.perform(get("/api/broadcasts/" + broadcastId))
				.andExpect(jsonPath("$.thumbnailUpdatedAt").doesNotExist());
	}

	@Test
	void 없으면_404() throws Exception {
		mockMvc.perform(get(url())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("THUMBNAIL_NOT_FOUND"));
	}

	@Test
	void 다른_사용자와_비로그인은_올릴_수_없다() throws Exception {
		setLive(false);
		mockMvc.perform(put(url()).cookie(other).contentType(MediaType.IMAGE_JPEG).content(JPEG))
				.andExpect(status().isForbidden());
		mockMvc.perform(put(url()).contentType(MediaType.IMAGE_JPEG).content(JPEG))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 방송_중이_아니면_올릴_수_없다() throws Exception {
		mockMvc.perform(put(url()).cookie(owner).contentType(MediaType.IMAGE_JPEG).content(JPEG))
				.andExpect(status().isConflict());
		setLive(true);
		mockMvc.perform(put(url()).cookie(owner).contentType(MediaType.IMAGE_JPEG).content(JPEG))
				.andExpect(jsonPath("$.code").value("BROADCAST_ENDED"));
	}

	@Test
	void JPEG가_아니거나_너무_크면_거절한다() throws Exception {
		setLive(false);
		mockMvc.perform(put(url()).cookie(owner).contentType(MediaType.IMAGE_JPEG).content(new byte[] {1, 2, 3, 4}))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_THUMBNAIL"));
		byte[] big = new byte[BroadcastThumbnailService.MAX_BYTES + 1];
		big[0] = (byte) 0xFF;
		big[1] = (byte) 0xD8;
		big[2] = (byte) 0xFF;
		mockMvc.perform(put(url()).cookie(owner).contentType(MediaType.IMAGE_JPEG).content(big))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put(url()).cookie(owner).contentType(MediaType.IMAGE_PNG).content(JPEG))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void 방송을_지우면_썸네일도_지워진다() throws Exception {
		setLive(false);
		mockMvc.perform(put(url()).cookie(owner).contentType(MediaType.IMAGE_JPEG).content(JPEG));
		Broadcast broadcast = broadcastRepository.findById(broadcastId).orElseThrow();
		broadcast.end();
		broadcastRepository.saveAndFlush(broadcast);
		mockMvc.perform(delete("/api/broadcasts/" + broadcastId).cookie(owner)).andExpect(status().isNoContent());
		mockMvc.perform(get(url())).andExpect(status().isNotFound());
	}
}
