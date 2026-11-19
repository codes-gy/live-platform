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
}
