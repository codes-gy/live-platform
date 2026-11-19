package com.gy.live.livekit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LiveKitNamingTest {

	@Test
	void 방_이름과_방송_ID를_오간다() {
		assertThat(RoomNames.of(42L)).isEqualTo("broadcast-42");
		assertThat(RoomNames.broadcastIdOf("broadcast-42")).contains(42L);
		assertThat(RoomNames.broadcastIdOf("broadcast-abc")).isEmpty();
		assertThat(RoomNames.broadcastIdOf("other-42")).isEmpty();
		assertThat(RoomNames.broadcastIdOf(null)).isEmpty();
	}

	@Test
	void 방송자_참가자를_구분한다() {
		assertThat(RoomNames.isBroadcaster(RoomNames.broadcasterIdentity(7L))).isTrue();
		assertThat(RoomNames.isBroadcaster("viewer-1")).isFalse();
		assertThat(RoomNames.isBroadcaster(null)).isFalse();
	}

	@Test
	void 클라이언트_주소를_서버_API_주소로_바꾼다() {
		assertThat(SdkLiveKitRoomClient.httpUrl("wss://x.livekit.cloud")).isEqualTo("https://x.livekit.cloud");
		assertThat(SdkLiveKitRoomClient.httpUrl("ws://localhost:7880")).isEqualTo("http://localhost:7880");
		assertThat(SdkLiveKitRoomClient.httpUrl("https://x.livekit.cloud")).isEqualTo("https://x.livekit.cloud");
	}
}
