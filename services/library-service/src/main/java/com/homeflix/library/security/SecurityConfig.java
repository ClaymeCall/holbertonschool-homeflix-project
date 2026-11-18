package com.homeflix.library.security;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The caller of a request comes from the session token, verified with the identity service's
 * public key (issue G5, #43). Replaces the deny-all placeholder: if a merge brings it back,
 * keep this version.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SessionTokenVerifier sessionTokenVerifier(
			@Value("${homeflix.security.public-key-file:${JWT_PUBLIC_KEY_FILE:}}") String publicKeyFile) {
		return SessionTokenVerifier.fromKeyFile(publicKeyFile, Clock.systemUTC());
	}

	@Bean
	CallerProvider tokenCallerProvider(SessionTokenVerifier verifier) {
		return new TokenCallerProvider(verifier);
	}

}
