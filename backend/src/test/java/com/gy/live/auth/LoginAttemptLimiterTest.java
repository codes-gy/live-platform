package com.gy.live.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Docker 없이 도는 단위 테스트. 시간은 직접 움직인다. */
class LoginAttemptLimiterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-25T00:00:00Z"));
	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(10), clock);

	@Test
	void 한도만큼_틀리면_window가_끝날_때까지_막는다() {
		for (int i = 0; i < 3; i++) {
			limiter.checkAllowed("1.1.1.1", "a@x.com");
			limiter.recordFailure("1.1.1.1", "a@x.com");
		}

		assertThatThrownBy(() -> limiter.checkAllowed("1.1.1.1", "a@x.com"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
		// 다른 IP · 다른 이메일은 따로 센다
		assertThatCode(() -> limiter.checkAllowed("2.2.2.2", "a@x.com")).doesNotThrowAnyException();
		assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "b@x.com")).doesNotThrowAnyException();

		clock.advance(Duration.ofMinutes(10));
		assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "a@x.com")).doesNotThrowAnyException();
	}

	@Test
	void 한도_아래면_막지_않고_성공하면_기록을_지운다() {
		limiter.recordFailure("1.1.1.1", "a@x.com");
		limiter.recordFailure("1.1.1.1", "a@x.com");
		assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "a@x.com")).doesNotThrowAnyException();

		limiter.recordSuccess("1.1.1.1", "a@x.com");
		limiter.recordFailure("1.1.1.1", "a@x.com");
		limiter.recordFailure("1.1.1.1", "a@x.com");
		assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "a@x.com")).doesNotThrowAnyException();
	}

	@Test
	void window가_지난_실패는_새로_센다() {
		limiter.recordFailure("1.1.1.1", "a@x.com");
		limiter.recordFailure("1.1.1.1", "a@x.com");
		clock.advance(Duration.ofMinutes(11));
		limiter.recordFailure("1.1.1.1", "a@x.com");

		assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "a@x.com")).doesNotThrowAnyException();
	}

	@Test
	void 끝난_기록은_정리한다() {
		limiter.recordFailure("1.1.1.1", "a@x.com");
		limiter.recordFailure("2.2.2.2", "a@x.com");
		clock.advance(Duration.ofMinutes(5));
		limiter.recordFailure("3.3.3.3", "a@x.com");
		clock.advance(Duration.ofMinutes(5));

		limiter.sweep();

		assertThat(limiter.size()).isEqualTo(1);
	}

	@Test
	void 잘못된_설정은_거절한다() {
		assertThatThrownBy(() -> new LoginAttemptLimiter(0, Duration.ofMinutes(1), clock))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new LoginAttemptLimiter(1, Duration.ZERO, clock))
				.isInstanceOf(IllegalArgumentException.class);
	}

	static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
