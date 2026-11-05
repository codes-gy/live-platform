package com.gy.live.livekit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class LiveKitConfig {

	/** 방송자가 나간 뒤 일정 시간 뒤에 돌아왔는지 확인하는 예약 작업용. */
	@Bean(destroyMethod = "shutdown")
	ThreadPoolTaskScheduler liveTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(2);
		scheduler.setThreadNamePrefix("live-");
		scheduler.initialize();
		return scheduler;
	}
}
