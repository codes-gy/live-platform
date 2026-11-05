package com.gy.live.auth;

import com.gy.live.global.config.AppProperties;
import com.gy.live.user.User;
import com.gy.live.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 서버가 뜰 때 테스트 계정이 없으면 만든다. 이미 있으면 그대로 둔다(재시작해도 같은 계정). */
@Component
public class TestAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TestAccountInitializer.class);

	private final AppProperties properties;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public TestAccountInitializer(AppProperties properties, UserRepository userRepository,
			PasswordEncoder passwordEncoder) {
		this.properties = properties;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!properties.testAccounts().enabled()) {
			return;
		}
		String hash = passwordEncoder.encode(properties.testAccounts().password());
		for (TestAccount account : TestAccount.values()) {
			if (!userRepository.existsByEmail(account.email())) {
				userRepository.save(new User(account.email(), hash, account.nickname()));
				log.info("테스트 계정 생성: {}", account.email());
			}
		}
	}
}
