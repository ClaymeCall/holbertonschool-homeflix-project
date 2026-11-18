package com.homeflix.library.security;

import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Finds the caller from the session cookie ({@code homeflix_token}), verified with the identity
 * service's public key (issue G5). Never from a URL, a body or a header (R-06, PRF-04).
 */
public class TokenCallerProvider implements CallerProvider {

	static final String COOKIE = "homeflix_token";

	private final SessionTokenVerifier verifier;

	public TokenCallerProvider(SessionTokenVerifier verifier) {
		this.verifier = verifier;
	}

	@Override
	public Optional<Caller> current(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (COOKIE.equals(cookie.getName())) {
				return verifier.verify(cookie.getValue());
			}
		}
		return Optional.empty();
	}

}
