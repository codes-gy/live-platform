package com.gy.live.support;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 테스트에서 JWT 본문(서명 검증 없이)을 읽는다. */
public final class JwtClaims {

	private JwtClaims() {
	}

	public static <T> T read(String jwt, String path) {
		String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
		return JsonPath.read(payload, path);
	}
}
