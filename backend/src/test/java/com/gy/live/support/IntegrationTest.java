package com.gy.live.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** 실제 PostgreSQL(Testcontainers) + 전체 스프링 컨텍스트 + MockMvc. 같은 설정끼리 컨텍스트를 재사용한다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
		"app.livekit.url=wss://test.livekit.cloud",
		"app.livekit.api-key=" + IntegrationTest.LIVEKIT_KEY,
		"app.livekit.api-secret=" + IntegrationTest.LIVEKIT_SECRET,
		"app.livekit.broadcaster-grace=200ms",
		// 타이머는 테스트 중 돌지 않게 하고, 정리는 reconcileLive()를 직접 불러 확인한다
		"app.livekit.reconcile-interval=1h",
		"app.toss.client-key=test_ck_fake",
		"app.toss.secret-key=test_sk_fake",
		// 후원 정리 작업도 테스트에서는 reconcileConfirming()을 직접 불러 확인한다
		"app.toss.reconcile-interval=1h"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, FakeLiveKitConfig.class})
public @interface IntegrationTest {

	String LIVEKIT_KEY = "test-api-key";
	String LIVEKIT_SECRET = "test-api-secret-test-api-secret-0123456789";
}
