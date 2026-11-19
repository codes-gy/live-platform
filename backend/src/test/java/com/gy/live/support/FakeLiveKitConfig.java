package com.gy.live.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class FakeLiveKitConfig {

	@Bean
	@Primary
	FakeLiveKitRoomClient fakeLiveKitRoomClient() {
		return new FakeLiveKitRoomClient();
	}

	@Bean
	@Primary
	FakeLiveKitRecorder fakeLiveKitRecorder() {
		return new FakeLiveKitRecorder();
	}

	@Bean
	@Primary
	FakeRecordingStorage fakeRecordingStorage() {
		return new FakeRecordingStorage();
	}

	@Bean
	@Primary
	FakeTossPaymentsClient fakeTossPaymentsClient() {
		return new FakeTossPaymentsClient();
	}
}
