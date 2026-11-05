package com.gy.live.global.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
		List<String> corsAllowedOrigins,
		Jwt jwt,
		Cookie cookie,
		TestAccounts testAccounts,
		Livekit livekit
) {

	/** secret은 HS256 서명용으로 32바이트 이상이어야 한다. */
	public record Jwt(String secret, Duration accessTokenTtl) {
	}

	/** 운영(HTTPS)에서는 secure=true. 로컬 http에서는 false여야 브라우저가 쿠키를 저장한다. */
	public record Cookie(boolean secure) {
	}

	/**
	 * LiveKit Cloud 접속 정보와 방 수명 설정.
	 *
	 * @param url              클라이언트가 접속할 주소(wss://…). 서버 API는 같은 호스트의 https로 부른다
	 * @param emptyTimeout     방을 만든 뒤 아무도 들어오지 않으면 닫는 시간
	 * @param departureTimeout 마지막 참가자가 나간 뒤 방을 닫는 시간(새로고침 재접속 여유)
	 * @param broadcasterGrace 방송자가 나간 뒤 돌아오지 않으면 방송을 종료하기까지 기다리는 시간
	 * @param tokenTtl         접속 토큰 유효 시간(접속할 때만 쓰고, 접속 중에는 LiveKit이 갱신한다)
	 */
	public record Livekit(String url, String apiKey, String apiSecret, Duration emptyTimeout,
			Duration departureTimeout, Duration broadcasterGrace, Duration tokenTtl) {

		public boolean configured() {
			return isPresent(url) && isPresent(apiKey) && isPresent(apiSecret);
		}

		private static boolean isPresent(String value) {
			return value != null && !value.isBlank();
		}
	}

	/** 심사자용 테스트 계정. 비밀번호는 README에 공개하는 값이라 민감 정보가 아니다. */
	public record TestAccounts(boolean enabled, String password) {
	}
}
