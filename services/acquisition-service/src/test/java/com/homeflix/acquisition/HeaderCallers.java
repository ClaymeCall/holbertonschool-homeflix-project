package com.homeflix.acquisition;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.homeflix.acquisition.security.Caller;
import com.homeflix.acquisition.security.CallerProvider;

/**
 * Stands in for the token check of issue G5 in tests: the {@code X-Test-Caller} header says who is
 * calling ({@code admin} or {@code user}), and a request without it is anonymous.
 */
@TestConfiguration(proxyBeanMethods = false)
public class HeaderCallers {

	@Bean
	@Primary
	CallerProvider headerCallers() {
		return request -> switch (String.valueOf(request.getHeader("X-Test-Caller"))) {
			case "admin" -> Optional.of(new Caller(UUID.randomUUID(), true, null));
			case "user" -> Optional.of(new Caller(UUID.randomUUID(), false, UUID.randomUUID()));
			default -> Optional.empty();
		};
	}

}
