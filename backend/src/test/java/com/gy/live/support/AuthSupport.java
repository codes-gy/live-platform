package com.gy.live.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.gy.live.global.security.AuthCookies;
import jakarta.servlet.http.Cookie;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 테스트에서 로그인 쿠키를 얻는 도우미. */
public final class AuthSupport {

	private AuthSupport() {
	}

	/** 새 사용자를 가입시켜 다른 테스트의 데이터와 섞이지 않게 한다. */
	public static Cookie signupNewUser(MockMvc mockMvc, String nickname) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s@example.com","password":"password123","nickname":"%s"}
								""".formatted(UUID.randomUUID(), nickname)))
				.andReturn();
		return Objects.requireNonNull(result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN));
	}
}
