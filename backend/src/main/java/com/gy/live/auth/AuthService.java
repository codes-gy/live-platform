package com.gy.live.auth;

import com.gy.live.auth.dto.LoginRequest;
import com.gy.live.auth.dto.SignupRequest;
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
	private final LoginAttemptLimiter loginLimiter;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			LoginAttemptLimiter loginLimiter) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.loginLimiter = loginLimiter;
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

	/**
	 * clientIp: 무차별 대입 방지용 접속 IP. (IP, 이메일)마다 여러 번 틀리면 한동안 비밀번호를 확인하지 않고 429.
	 * 막혀 있으면 비밀번호가 맞아도 거절한다(맞는 비밀번호를 찾았는지 알 수 없게).
	 */
	@Transactional(readOnly = true)
	public User login(LoginRequest request, String clientIp) {
		String email = normalize(request.email());
		loginLimiter.checkAllowed(clientIp, email);
		User user = userRepository.findByEmail(email)
				.filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
				.orElse(null);
		if (user == null) {
			loginLimiter.recordFailure(clientIp, email);
			throw new BusinessException(ErrorCode.LOGIN_FAILED);
		}
		loginLimiter.recordSuccess(clientIp, email);
		return user;
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
