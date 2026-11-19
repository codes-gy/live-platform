package com.gy.live.global.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
		List<String> corsAllowedOrigins,
		Jwt jwt,
		Cookie cookie,
		Livekit livekit,
		Toss toss,
		Recording recording
) {

	/**
	 * 녹화(LiveKit Egress가 Cloudflare R2에 MP4로 올린다). 비어 있으면 녹화하지 않고 방송만 한다.
	 *
	 * @param r2AccountId       Cloudflare 계정 ID(엔드포인트 https://{id}.r2.cloudflarestorage.com)
	 * @param r2AccessKeyId     R2 API 토큰의 Access Key ID
	 * @param r2SecretAccessKey R2 API 토큰의 Secret Access Key(서버에서만 쓴다)
	 * @param r2Bucket          비공개 버킷
	 * @param playbackTtl       재생용 서명 주소 유효 시간
	 */
	public record Recording(String r2AccountId, String r2AccessKeyId, String r2SecretAccessKey, String r2Bucket,
			java.time.Duration playbackTtl) {

		public boolean configured() {
			return isPresent(r2AccountId) && isPresent(r2AccessKeyId) && isPresent(r2SecretAccessKey) && isPresent(r2Bucket);
		}

		public String endpoint() {
			return "https://" + r2AccountId + ".r2.cloudflarestorage.com";
		}

		private static boolean isPresent(String value) {
			return value != null && !value.isBlank();
		}
	}

	/**
	 * 토스페이먼츠(API 개별 연동, 테스트 모드 키 test_ck_ · test_sk_).
	 *
	 * @param clientKey 결제창을 여는 공개 키(브라우저에 내려준다)
	 * @param secretKey 결제 승인용 비밀 키(서버에서만 쓴다)
	 */
	public record Toss(String clientKey, String secretKey) {

		public boolean configured() {
			return clientKey != null && !clientKey.isBlank() && secretKey != null && !secretKey.isBlank();
		}
	}

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
}
