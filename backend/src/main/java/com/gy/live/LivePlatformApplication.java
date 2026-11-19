package com.gy.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LivePlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(LivePlatformApplication.class, args);
	}

}
