package com.homeflix.identity.session;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SessionConfig {

	/** A bean so tests can check expiry without waiting. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
