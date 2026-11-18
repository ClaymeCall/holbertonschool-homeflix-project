package com.homeflix.library.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.Cookie;

/**
 * The caller comes from the session cookie only, never from a header or a parameter (R-06).
 */
class TokenCallerProviderTests {

	private final TokenCallerProvider callers = new TokenCallerProvider(
			new SessionTokenVerifier(TestTokens.identityPublicKey(), Clock.systemUTC()));
	private final UUID accountId = UUID.randomUUID();

	@Test
	void requestWithoutCookieIsAnonymous() {
		assertThat(callers.current(new MockHttpServletRequest())).isEmpty();
	}

	@Test
	void callerComesFromTheSessionCookie() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("other", "x"), new Cookie("homeflix_token", TestTokens.admin(accountId)));

		assertThat(callers.current(request)).contains(new Caller(accountId, true, null));
	}

	@Test
	void tokenInAHeaderOrParameterIsIgnored() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + TestTokens.admin(accountId));
		request.addParameter("token", TestTokens.admin(accountId));
		request.addHeader("X-Account-Id", accountId.toString());

		assertThat(callers.current(request)).isEmpty();
	}

	@Test
	void invalidCookieIsAnonymous() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("homeflix_token", "not-a-token"));

		assertThat(callers.current(request)).isEmpty();
	}

}
