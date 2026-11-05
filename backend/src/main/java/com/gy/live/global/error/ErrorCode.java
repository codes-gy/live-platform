package com.gy.live.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 오류가 발생했습니다."),

	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	TEST_LOGIN_DISABLED(HttpStatus.NOT_FOUND, "테스트 계정 로그인이 꺼져 있습니다."),

	BROADCAST_NOT_FOUND(HttpStatus.NOT_FOUND, "방송을 찾을 수 없습니다."),
	NOT_BROADCAST_OWNER(HttpStatus.FORBIDDEN, "방송 소유자만 할 수 있습니다."),
	BROADCAST_ALREADY_LIVE(HttpStatus.CONFLICT, "이미 방송 중입니다."),
	BROADCAST_NOT_LIVE(HttpStatus.CONFLICT, "방송 중이 아닙니다."),
	BROADCAST_ENDED(HttpStatus.CONFLICT, "이미 종료된 방송입니다."),
	BROADCAST_LIVE_CANNOT_DELETE(HttpStatus.CONFLICT, "방송 중에는 삭제할 수 없습니다. 방송을 먼저 종료해 주세요."),

	LIVEKIT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "방송 서버 설정이 없어 송출 · 시청할 수 없습니다."),
	LIVEKIT_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "방송 서버와 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."),
	INVALID_WEBHOOK(HttpStatus.UNAUTHORIZED, "웹훅 서명이 올바르지 않습니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}
}
