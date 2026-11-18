package com.homeflix.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.homeflix.acquisition.security.CallerProvider;
import com.homeflix.acquisition.security.TokenCallerProvider;

/**
 * The running service finds callers from the session token, with the public key from
 * configuration (G5). Nothing else, such as a header set by the gateway, can say who is calling.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SecurityWiringTests {

	@Autowired
	private CallerProvider callers;

	@Test
	void callersComeFromTheSessionToken() {
		assertThat(callers).isInstanceOf(TokenCallerProvider.class);
	}

}
