package com.gy.live.recording;

import static org.assertj.core.api.Assertions.assertThat;

import com.gy.live.global.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class R2RecordingStorageTest {

	private final AppProperties.Recording props = new AppProperties.Recording(
			"0123456789abcdef0123456789abcdef", "AKIDEXAMPLE", "secret", "jiing-recordings", Duration.ofHours(2));

	@Test
	void 서명된_GET_주소를_만든다() {
		Clock clock = Clock.fixed(Instant.parse("2026-11-20T01:02:03Z"), ZoneOffset.UTC);
		String url = new R2RecordingStorage(props, clock).playbackUrl("recordings/broadcast-1/a b.mp4");

		assertThat(url).startsWith("https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com"
				+ "/jiing-recordings/recordings/broadcast-1/a%20b.mp4?")
				.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
				.contains("X-Amz-Credential=AKIDEXAMPLE%2F20261120%2Fauto%2Fs3%2Faws4_request")
				.contains("X-Amz-Date=20261120T010203Z")
				.contains("X-Amz-Expires=7200")
				.contains("X-Amz-SignedHeaders=host")
				.containsPattern("X-Amz-Signature=[0-9a-f]{64}$");
	}

	@Test
	void 같은_시각_같은_입력이면_같은_서명() {
		Clock clock = Clock.fixed(Instant.parse("2026-11-20T01:02:03Z"), ZoneOffset.UTC);
		R2RecordingStorage storage = new R2RecordingStorage(props, clock);

		assertThat(storage.playbackUrl("k.mp4")).isEqualTo(storage.playbackUrl("k.mp4"));
	}

	@Test
	void 경로는_슬래시를_남기고_나머지를_인코딩한다() {
		assertThat(R2RecordingStorage.encodePath("a/b c/한.mp4")).isEqualTo("a/b%20c/%ED%95%9C.mp4");
	}
}
