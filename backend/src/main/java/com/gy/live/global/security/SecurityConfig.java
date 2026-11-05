package com.gy.live.global.security;

import com.gy.live.global.config.AppProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	/** 로그인 없이 쓰는 경로. 방송 목록 · 상세 · 시청은 공모전 요구사항상 비로그인도 허용한다. */
	private static final String[] PUBLIC_POST = {
			"/api/auth/signup", "/api/auth/login", "/api/auth/logout", "/api/auth/test-login",
			// 시청 토큰은 비로그인도 받는다. 웹훅은 LiveKit 서명으로 따로 검증한다
			"/api/broadcasts/*/viewer-token", "/api/livekit/webhook"
	};
	private static final String[] PUBLIC_GET = {
			"/api/broadcasts", "/api/broadcasts/**",
			"/actuator/health", "/swagger-ui/**", "/v3/api-docs/**"
	};

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
			SecurityErrorHandlers errorHandlers) throws Exception {
		return http
				// 세션 대신 HttpOnly + SameSite=Lax 쿠키의 JWT를 쓴다. 다른 사이트 요청에는 쿠키가 붙지 않아 CSRF 토큰을 따로 두지 않는다.
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> {})
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
						.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(e -> e
						.authenticationEntryPoint(errorHandlers.entryPoint())
						.accessDeniedHandler(errorHandlers.accessDeniedHandler()))
				.addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.corsAllowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}
}
