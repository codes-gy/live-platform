package com.gy.live.global.security;

import com.gy.live.global.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

	private static final String NICKNAME = "nickname";

	private final SecretKey key;
	private final Duration ttl;

	public JwtProvider(AppProperties properties) {
		this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
		this.ttl = properties.jwt().accessTokenTtl();
	}

	public String issue(LoginUser user) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(user.id()))
				.claim(NICKNAME, user.nickname())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(ttl)))
				.signWith(key)
				.compact();
	}

	/** 서명 · 만료가 올바르면 주체를, 아니면 빈 값을 돌려준다. */
	public Optional<LoginUser> parse(String token) {
		try {
			Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
			return Optional.of(new LoginUser(Long.valueOf(claims.getSubject()), claims.get(NICKNAME, String.class)));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public Duration ttl() {
		return ttl;
	}
}
