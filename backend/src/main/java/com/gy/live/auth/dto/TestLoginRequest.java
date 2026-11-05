package com.gy.live.auth.dto;

import com.gy.live.auth.TestAccount;
import jakarta.validation.constraints.NotNull;

public record TestLoginRequest(
		@NotNull(message = "테스트 계정을 골라 주세요. (STREAMER 또는 VIEWER)") TestAccount account
) {
}
