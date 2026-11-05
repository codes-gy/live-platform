package com.gy.live.global.security;

/** 인증된 요청의 주체. 컨트롤러에서 {@code @AuthenticationPrincipal LoginUser}로 받는다. */
public record LoginUser(Long id, String nickname) {
}
