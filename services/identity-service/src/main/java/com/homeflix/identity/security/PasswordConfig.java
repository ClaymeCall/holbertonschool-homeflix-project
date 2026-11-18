package com.homeflix.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing with BCrypt (R-08, ACC-04). Passwords are never stored in plain text.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

	/** R-08 allows a strength of 10 to 12. */
	static final int BCRYPT_STRENGTH = 12;

	/** BCrypt only uses the first 72 bytes of a password; longer input is rejected. */
	public static final int BCRYPT_MAX_BYTES = 72;

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
	}

}
