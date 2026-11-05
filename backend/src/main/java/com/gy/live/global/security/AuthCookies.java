package com.gy.live.global.security;

import com.gy.live.global.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 접근 토큰을 HttpOnly 쿠키로 주고받는다. 스크립트가 토큰을 읽을 수 없어 XSS로 토큰이 새지 않고,
 * SameSite=Lax라 다른 사이트에서 보낸 POST · PATCH · DELETE에는 쿠키가 붙지 않는다(CSRF 방어).
 */
@Component
public class AuthCookies {

	public static final String ACCESS_TOKEN = "access_token";

	private final boolean secure;

	public AuthCookies(AppProperties properties) {
		this.secure = properties.cookie().secure();
	}

	public ResponseCookie issue(String token, Duration maxAge) {
		return base(token).maxAge(maxAge).build();
	}

	public ResponseCookie expire() {
		return base("").maxAge(0).build();
	}

	private ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(ACCESS_TOKEN, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax")
				.path("/");
	}
}
