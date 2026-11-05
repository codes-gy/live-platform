package com.gy.live.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 쿠키(브라우저) 또는 Authorization: Bearer(Swagger · curl) 에서 토큰을 읽어 인증한다. 토큰이 없거나 틀리면 익명으로 둔다. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final JwtProvider jwtProvider;

	public JwtAuthenticationFilter(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		resolveToken(request)
				.flatMap(jwtProvider::parse)
				.ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
						new UsernamePasswordAuthenticationToken(user, null, List.of())));
		chain.doFilter(request, response);
	}

	private Optional<String> resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER)) {
			return Optional.of(header.substring(BEARER.length()));
		}
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
				.filter(cookie -> AuthCookies.ACCESS_TOKEN.equals(cookie.getName()))
				.map(Cookie::getValue)
				.filter(value -> !value.isBlank())
				.findFirst();
	}
}
