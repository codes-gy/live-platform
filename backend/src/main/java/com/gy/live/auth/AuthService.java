package com.gy.live.auth;

import com.gy.live.auth.dto.LoginRequest;
import com.gy.live.auth.dto.SignupRequest;
import com.gy.live.global.config.AppProperties;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.user.User;
import com.gy.live.user.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AppProperties properties;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AppProperties properties) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Transactional
	public User signup(SignupRequest request) {
		String email = normalize(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
		try {
			return userRepository.saveAndFlush(
					new User(email, passwordEncoder.encode(request.password()), request.nickname().strip()));
		} catch (DataIntegrityViolationException e) {
			// 같은 이메일로 동시에 가입하면 존재 확인을 둘 다 통과할 수 있다. 유니크 제약이 마지막 방어선이다.
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
	}

	@Transactional(readOnly = true)
	public User login(LoginRequest request) {
		return userRepository.findByEmail(normalize(request.email()))
				.filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
				.orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
	}

	@Transactional(readOnly = true)
	public User testLogin(TestAccount account) {
		if (!properties.testAccounts().enabled()) {
			throw new BusinessException(ErrorCode.TEST_LOGIN_DISABLED);
		}
		return userRepository.findByEmail(account.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.TEST_LOGIN_DISABLED));
	}

	@Transactional(readOnly = true)
	public User me(Long userId) {
		// 토큰은 유효해도 계정이 사라졌을 수 있다. 그때는 다시 로그인하게 401로 돌려준다.
		return userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	private String normalize(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}
}
