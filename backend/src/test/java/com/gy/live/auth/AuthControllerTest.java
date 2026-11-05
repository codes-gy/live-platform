package com.gy.live.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gy.live.global.security.AuthCookies;
import com.gy.live.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class AuthControllerTest {

	@Autowired
	MockMvc mockMvc;

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private MvcResult signup(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s","nickname":"방송러"}
								""".formatted(email, password)))
				.andReturn();
	}

	private Cookie tokenCookie(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN);
		assertThat(cookie).isNotNull();
		return cookie;
	}

	@Nested
	@DisplayName("회원가입")
	class Signup {

		@Test
		void 가입하면_201과_HttpOnly_토큰_쿠키를_준다() throws Exception {
			String email = uniqueEmail();
			MvcResult result = signup(email, "password123");

			assertThat(result.getResponse().getStatus()).isEqualTo(201);
			assertThat(result.getResponse().getContentAsString()).contains(email);
			String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
			assertThat(setCookie).contains("access_token=", "HttpOnly", "SameSite=Lax", "Path=/");
		}

		@Test
		void 이미_있는_이메일이면_409() throws Exception {
			String email = uniqueEmail();
			signup(email, "password123");

			mockMvc.perform(post("/api/auth/signup")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"%s","password":"password123","nickname":"다른사람"}
									""".formatted(email.toUpperCase())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
		}

		@Test
		void 비밀번호가_8자_미만이면_400과_이유를_준다() throws Exception {
			mockMvc.perform(post("/api/auth/signup")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"%s","password":"short","nickname":"방송러"}
									""".formatted(uniqueEmail())))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
					.andExpect(jsonPath("$.message").value("비밀번호는 8자 이상 64자 이하로 입력해 주세요."));
		}
	}

	@Nested
	@DisplayName("로그인 · 내 정보 · 로그아웃")
	class Login {

		@Test
		void 로그인한_쿠키로_내_정보를_조회한다() throws Exception {
			String email = uniqueEmail();
			signup(email, "password123");

			MvcResult login = mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"%s","password":"password123"}
									""".formatted(email)))
					.andExpect(status().isOk())
					.andReturn();

			mockMvc.perform(get("/api/auth/me").cookie(tokenCookie(login)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.email").value(email))
					.andExpect(jsonPath("$.nickname").value("방송러"));
		}

		@Test
		void Bearer_헤더로도_인증된다() throws Exception {
			MvcResult result = signup(uniqueEmail(), "password123");

			mockMvc.perform(get("/api/auth/me")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCookie(result).getValue()))
					.andExpect(status().isOk());
		}

		@Test
		void 비밀번호가_틀리면_401() throws Exception {
			String email = uniqueEmail();
			signup(email, "password123");

			mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"%s","password":"wrong-password"}
									""".formatted(email)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
					.andExpect(cookie().doesNotExist(AuthCookies.ACCESS_TOKEN));
		}

		@Test
		void 없는_이메일도_같은_401_메시지() throws Exception {
			mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"%s","password":"password123"}
									""".formatted(uniqueEmail())))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		}

		@Test
		void 로그인_없이_내_정보를_조회하면_401_JSON() throws Exception {
			mockMvc.perform(get("/api/auth/me"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		}

		@Test
		void 위조한_토큰은_401() throws Exception {
			mockMvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, "not-a-jwt")))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void 로그아웃하면_쿠키를_지운다() throws Exception {
			mockMvc.perform(post("/api/auth/logout"))
					.andExpect(status().isNoContent())
					.andExpect(cookie().maxAge(AuthCookies.ACCESS_TOKEN, 0));
		}
	}

	@Nested
	@DisplayName("테스트 계정 로그인")
	class TestLogin {

		@Test
		void 방송자_테스트_계정으로_바로_로그인한다() throws Exception {
			MvcResult result = mockMvc.perform(post("/api/auth/test-login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"account":"STREAMER"}
									"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.email").value("streamer@test.com"))
					.andReturn();

			mockMvc.perform(get("/api/auth/me").cookie(tokenCookie(result)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.nickname").value("테스트방송자"));
		}

		@Test
		void 테스트_계정은_공개_비밀번호로도_로그인된다() throws Exception {
			mockMvc.perform(post("/api/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"viewer@test.com","password":"test1234!"}
									"""))
					.andExpect(status().isOk());
		}

		@Test
		void 없는_계정_종류면_400() throws Exception {
			mockMvc.perform(post("/api/auth/test-login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"account":"ADMIN"}
									"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		}
	}
}
