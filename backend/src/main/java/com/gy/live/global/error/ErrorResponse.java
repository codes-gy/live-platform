package com.gy.live.global.error;

/** 모든 오류 응답의 공통 형식. code는 프론트가 분기할 때, message는 사용자에게 그대로 보여 줄 때 쓴다. */
public record ErrorResponse(String code, String message) {

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.name(), errorCode.message());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.name(), message);
	}
}
