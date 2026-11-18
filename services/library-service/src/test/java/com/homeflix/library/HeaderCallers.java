package com.homeflix.library;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.homeflix.library.security.Caller;
import com.homeflix.library.security.CallerProvider;

/**
 * Stands in for the token check of issue G5 in tests: the {@code X-Test-Caller} header says who is
 * calling ({@code admin} or {@code user}), and a request without it is anonymous. {@code X-Test-Profile} sets the
 * active profile of a user.
 */
@TestConfiguration(proxyBeanMethods = false)
public class HeaderCallers {

	@Bean
	@Primary
	CallerProvider headerCallers() {
		return request -> switch (String.valueOf(request.getHeader("X-Test-Caller"))) {
			case "admin" -> Optional.of(new Caller(UUID.randomUUID(), true, null));
			case "user" -> Optional.of(new Caller(UUID.randomUUID(), false, profileOf(request)));
			default -> Optional.empty();
		};
	}

	/** {@code X-Test-Profile} is a profile id, or {@code none} for a token without a profile. Random when absent. */
	private static UUID profileOf(jakarta.servlet.http.HttpServletRequest request) {
		String header = request.getHeader("X-Test-Profile");
		if (header == null) {
			return UUID.randomUUID();
		}
		return "none".equals(header) ? null : UUID.fromString(header);
	}

}
