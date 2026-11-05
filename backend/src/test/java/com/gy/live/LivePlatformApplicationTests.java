package com.gy.live;

import com.gy.live.support.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class LivePlatformApplicationTests {

	@Test
	void contextLoads() {
	}
}
