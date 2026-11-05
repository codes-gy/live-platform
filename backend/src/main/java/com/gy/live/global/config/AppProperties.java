package com.gy.live.global.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
		List<String> corsAllowedOrigins,
		Jwt jwt,
		Cookie cookie,
		TestAccounts testAccounts
) {

	/** secret은 HS256 서명용으로 32바이트 이상이어야 한다. */
	public record Jwt(String secret, Duration accessTokenTtl) {
	}

	/** 운영(HTTPS)에서는 secure=true. 로컬 http에서는 false여야 브라우저가 쿠키를 저장한다. */
	public record Cookie(boolean secure) {
	}

	/** 심사자용 테스트 계정. 비밀번호는 README에 공개하는 값이라 민감 정보가 아니다. */
	public record TestAccounts(boolean enabled, String password) {
	}
}
