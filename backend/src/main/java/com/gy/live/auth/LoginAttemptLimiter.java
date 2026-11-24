package com.gy.live.auth;

import com.gy.live.global.config.AppProperties;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 로그인 무차별 대입 방지. (접속 IP, 이메일) 쌍마다 window 안에서 maxFailures번 틀리면
 * 그 window가 끝날 때까지 비밀번호를 확인하지 않고 429로 거절한다. 로그인에 성공하면 기록을 지운다.
 *
 * <p>기록은 메모리에 둔다: 서버 한 대 배포 기준이다. 여러 대로 늘리면 서버마다 따로 세므로
 * 한도가 대수만큼 느슨해진다(그때는 Redis 등 공유 저장소로 옮긴다). 재시작하면 기록이 사라진다.
 */
@Component
public class LoginAttemptLimiter {

	/** 기록이 이만큼 쌓이면 끝난 기록을 바로 치운다(주기 정리 전에 메모리가 늘지 않게). */
	static final int SWEEP_THRESHOLD = 10_000;

	private final int maxFailures;
	private final Duration window;
	private final Clock clock;
	private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

	@Autowired
	public LoginAttemptLimiter(AppProperties props) {
		this(props.login() == null ? 5 : props.login().maxFailures(),
				props.login() == null ? Duration.ofMinutes(15) : props.login().window(), Clock.systemUTC());
	}

	LoginAttemptLimiter(int maxFailures, Duration window, Clock clock) {
		if (maxFailures < 1 || window == null || window.isNegative() || window.isZero()) {
			throw new IllegalArgumentException("app.login.max-failures는 1 이상, app.login.window는 0보다 커야 합니다.");
		}
		this.maxFailures = maxFailures;
		this.window = window;
		this.clock = clock;
	}

	/** 막힌 쌍이면 429. 비밀번호를 확인하기 전에 부른다. */
	public void checkAllowed(String clientIp, String email) {
		Attempts current = attempts.get(key(clientIp, email));
		if (current != null && current.isBlocked(clock.instant(), maxFailures, window)) {
			throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
		}
	}

	public void recordFailure(String clientIp, String email) {
		Instant now = clock.instant();
		attempts.compute(key(clientIp, email), (k, current) ->
				current == null || current.isExpired(now, window) ? new Attempts(1, now) : current.plusOne());
		if (attempts.size() > SWEEP_THRESHOLD) {
			sweep();
		}
	}

	public void recordSuccess(String clientIp, String email) {
		attempts.remove(key(clientIp, email));
	}

	/** window가 끝난 기록을 치운다. */
	@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
	void sweep() {
		Instant now = clock.instant();
		attempts.values().removeIf(a -> a.isExpired(now, window));
	}

	int size() {
		return attempts.size();
	}

	private static String key(String clientIp, String email) {
		return (clientIp == null ? "" : clientIp) + "|" + email;
	}

	/** 처음 틀린 시각부터 window 동안 센다(고정 창). */
	private record Attempts(int failures, Instant firstFailureAt) {

		Attempts plusOne() {
			return new Attempts(failures + 1, firstFailureAt);
		}

		boolean isExpired(Instant now, Duration window) {
			return !now.isBefore(firstFailureAt.plus(window));
		}

		boolean isBlocked(Instant now, int maxFailures, Duration window) {
			return failures >= maxFailures && !isExpired(now, window);
		}
	}
}
