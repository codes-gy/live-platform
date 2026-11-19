package com.gy.live.user.avatar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class UserAvatarControllerTest {

	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

	@Autowired
	MockMvc mockMvc;

	Cookie me;
	long myId;

	@BeforeEach
	void setUp() throws Exception {
		me = AuthSupport.signupNewUser(mockMvc, "사진주인");
		String body = mockMvc.perform(get("/api/auth/me").cookie(me)).andReturn().getResponse().getContentAsString();
		myId = ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String publicUrl() {
		return "/api/users/" + myId + "/avatar";
	}

	@Test
	void 올리면_누구나_받아_보고_내_정보와_내_방송_소유자_정보에_갱신_시각이_실린다() throws Exception {
		mockMvc.perform(get("/api/auth/me").cookie(me)).andExpect(jsonPath("$.avatarUpdatedAt").doesNotExist());

		mockMvc.perform(put("/api/users/me/avatar").cookie(me).contentType(MediaType.IMAGE_JPEG).content(JPEG))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(publicUrl()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_JPEG))
				.andExpect(content().bytes(JPEG));
		mockMvc.perform(get("/api/auth/me").cookie(me)).andExpect(jsonPath("$.avatarUpdatedAt").isNotEmpty());
		String created = mockMvc.perform(post("/api/broadcasts").cookie(me).contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"방송\"}"))
				.andReturn().getResponse().getContentAsString();
		mockMvc.perform(get("/api/broadcasts/" + JsonPath.read(created, "$.id")))
				.andExpect(jsonPath("$.owner.avatarUpdatedAt").isNotEmpty());
	}

	@Test
	void 지우면_404이고_갱신_시각도_사라진다() throws Exception {
		mockMvc.perform(put("/api/users/me/avatar").cookie(me).contentType(MediaType.IMAGE_JPEG).content(JPEG));
		mockMvc.perform(delete("/api/users/me/avatar").cookie(me)).andExpect(status().isNoContent());

		mockMvc.perform(get(publicUrl())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("AVATAR_NOT_FOUND"));
		mockMvc.perform(get("/api/auth/me").cookie(me)).andExpect(jsonPath("$.avatarUpdatedAt").doesNotExist());
	}

	@Test
	void 비로그인은_올리거나_지울_수_없다() throws Exception {
		mockMvc.perform(put("/api/users/me/avatar").contentType(MediaType.IMAGE_JPEG).content(JPEG))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/users/me/avatar")).andExpect(status().isUnauthorized());
	}

	@Test
	void JPEG가_아니거나_너무_크면_거절한다() throws Exception {
		mockMvc.perform(put("/api/users/me/avatar").cookie(me).contentType(MediaType.IMAGE_JPEG).content(new byte[] {1, 2, 3, 4}))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AVATAR"));
		byte[] big = new byte[UserAvatarController.MAX_BYTES + 1];
		big[0] = (byte) 0xFF;
		big[1] = (byte) 0xD8;
		big[2] = (byte) 0xFF;
		mockMvc.perform(put("/api/users/me/avatar").cookie(me).contentType(MediaType.IMAGE_JPEG).content(big))
				.andExpect(status().isBadRequest());
	}
}
