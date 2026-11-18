package com.homeflix.library.security;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	/**
	 * Safe by default: with no token verification in place, nobody is signed in, so protected
	 * endpoints answer 401. Issue G5 replaces it with the token-based provider (and removes this class).
	 */
	@Bean
	CallerProvider denyAllCallerProvider() {
		return request -> Optional.empty();
	}

}
