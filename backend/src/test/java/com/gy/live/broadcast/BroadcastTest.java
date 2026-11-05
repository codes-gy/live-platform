package com.gy.live.broadcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.user.User;
import org.junit.jupiter.api.Test;

class BroadcastTest {

	private Broadcast newBroadcast() {
		return new Broadcast(new User("a@test.com", "hash", "닉"), "제목", null);
	}

	@Test
	void READY에서_시작하면_LIVE가_되고_시작_시각이_찍힌다() {
		Broadcast broadcast = newBroadcast();
		broadcast.start();

		assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.LIVE);
		assertThat(broadcast.getStartedAt()).isNotNull();
	}

	@Test
	void LIVE에서_종료하면_ENDED가_되고_종료_시각이_찍힌다() {
		Broadcast broadcast = newBroadcast();
		broadcast.start();
		broadcast.end();

		assertThat(broadcast.getStatus()).isEqualTo(BroadcastStatus.ENDED);
		assertThat(broadcast.getEndedAt()).isNotNull();
	}

	@Test
	void 이미_방송_중이면_다시_시작할_수_없다() {
		Broadcast broadcast = newBroadcast();
		broadcast.start();

		assertThatThrownBy(broadcast::start)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.BROADCAST_ALREADY_LIVE);
	}

	@Test
	void 종료된_방송은_다시_시작할_수_없다() {
		Broadcast broadcast = newBroadcast();
		broadcast.start();
		broadcast.end();

		assertThatThrownBy(broadcast::start)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.BROADCAST_ENDED);
	}

	@Test
	void 시작하지_않은_방송은_종료할_수_없다() {
		assertThatThrownBy(newBroadcast()::end)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.BROADCAST_NOT_LIVE);
	}

	@Test
	void 방송_중에는_삭제할_수_없다() {
		Broadcast broadcast = newBroadcast();
		broadcast.start();

		assertThatThrownBy(broadcast::checkDeletable)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.BROADCAST_LIVE_CANNOT_DELETE);
	}
}
