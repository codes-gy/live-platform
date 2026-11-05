package com.gy.live.auth;

/**
 * 심사용 테스트 계정 두 개. 방송 소유자와 다른 사용자의 권한 차이를 바로 확인할 수 있게 한다.
 */
public enum TestAccount {
	STREAMER("streamer@test.com", "테스트방송자"),
	VIEWER("viewer@test.com", "테스트시청자");

	private final String email;
	private final String nickname;

	TestAccount(String email, String nickname) {
		this.email = email;
		this.nickname = nickname;
	}

	public String email() {
		return email;
	}

	public String nickname() {
		return nickname;
	}
}
