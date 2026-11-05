package com.gy.live.auth;

import com.gy.live.auth.dto.LoginRequest;
import com.gy.live.auth.dto.MeResponse;
import com.gy.live.auth.dto.SignupRequest;
import com.gy.live.auth.dto.TestLoginRequest;
import com.gy.live.global.security.AuthCookies;
import com.gy.live.global.security.JwtProvider;
import com.gy.live.global.security.LoginUser;
import com.gy.live.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final JwtProvider jwtProvider;
	private final AuthCookies authCookies;

	public AuthController(AuthService authService, JwtProvider jwtProvider, AuthCookies authCookies) {
		this.authService = authService;
		this.jwtProvider = jwtProvider;
		this.authCookies = authCookies;
	}

	/** 가입과 동시에 로그인 상태로 만든다. */
	@PostMapping("/signup")
	public ResponseEntity<MeResponse> signup(@Valid @RequestBody SignupRequest request) {
		return withToken(HttpStatus.CREATED, authService.signup(request));
	}

	@PostMapping("/login")
	public ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest request) {
		return withToken(HttpStatus.OK, authService.login(request));
	}

	/** 심사자가 비밀번호 입력 없이 STREAMER(방송자) · VIEWER(다른 사용자)로 로그인한다. */
	@PostMapping("/test-login")
	public ResponseEntity<MeResponse> testLogin(@Valid @RequestBody TestLoginRequest request) {
		return withToken(HttpStatus.OK, authService.testLogin(request.account()));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout() {
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, authCookies.expire().toString())
				.build();
	}

	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal LoginUser loginUser) {
		return MeResponse.from(authService.me(loginUser.id()));
	}

	private ResponseEntity<MeResponse> withToken(HttpStatus status, User user) {
		String token = jwtProvider.issue(new LoginUser(user.getId(), user.getNickname()));
		return ResponseEntity.status(status)
				.header(HttpHeaders.SET_COOKIE, authCookies.issue(token, jwtProvider.ttl()).toString())
				.body(MeResponse.from(user));
	}
}
