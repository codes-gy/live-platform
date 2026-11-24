package com.gy.live.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.broadcast.BroadcastEndedEvent;
import com.gy.live.broadcast.BroadcastRepository;
import com.gy.live.broadcast.BroadcastStatus;
import com.gy.live.global.config.AppProperties;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

/** 접속 IP당 동시 구독 수 제한. Docker 없이 도는 단위 테스트. */
class CommentStreamsLimitTest {

	private static final long LIVE_ID = 1L;
	private static final long MISSING_ID = 2L;

	private CommentStreams streams;

	@BeforeEach
	void setUp() {
		BroadcastRepository repository = mock(BroadcastRepository.class);
		Broadcast live = mock(Broadcast.class);
		when(live.getStatus()).thenReturn(BroadcastStatus.LIVE);
		when(repository.findById(anyLong())).thenReturn(Optional.empty());
		when(repository.findById(LIVE_ID)).thenReturn(Optional.of(live));
		AppProperties props = new AppProperties(null, null, null, null, null, null, null,
				new AppProperties.CommentStream(2));
		streams = new CommentStreams(repository, mock(TaskScheduler.class), props);
	}

	@Test
	void IP당_한도를_넘으면_429_다른_IP는_따로_센다() {
		streams.subscribe(LIVE_ID, "1.1.1.1");
		streams.subscribe(LIVE_ID, "1.1.1.1");

		assertThatThrownBy(() -> streams.subscribe(LIVE_ID, "1.1.1.1"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.TOO_MANY_STREAMS);
		assertThat(streams.connectionCount("1.1.1.1")).isEqualTo(2);
		assertThatCode(() -> streams.subscribe(LIVE_ID, "2.2.2.2")).doesNotThrowAnyException();
	}

	@Test
	void 연결이_끝나면_자리가_난다() {
		streams.subscribe(LIVE_ID, "1.1.1.1");
		streams.subscribe(LIVE_ID, "1.1.1.1");

		// 방송 종료로 연결을 모두 닫는다
		streams.onBroadcastEnded(new BroadcastEndedEvent(LIVE_ID));

		assertThat(streams.connectionCount("1.1.1.1")).isZero();
		assertThatCode(() -> streams.subscribe(LIVE_ID, "1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void 없는_방송이면_404이고_자리를_차지하지_않는다() {
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> streams.subscribe(MISSING_ID, "1.1.1.1"))
					.isInstanceOf(BusinessException.class)
					.extracting(e -> ((BusinessException) e).errorCode())
					.isEqualTo(ErrorCode.BROADCAST_NOT_FOUND);
		}
		assertThat(streams.connectionCount("1.1.1.1")).isZero();
	}

	@Test
	void 한도는_1_이상이어야_한다() {
		AppProperties zero = new AppProperties(null, null, null, null, null, null, null,
				new AppProperties.CommentStream(0));
		assertThatThrownBy(() -> new CommentStreams(mock(BroadcastRepository.class), mock(TaskScheduler.class), zero))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
