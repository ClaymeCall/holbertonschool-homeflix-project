package com.homeflix.identity.security;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.homeflix.identity.session.SessionCookies;
import com.homeflix.identity.session.TokenService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller comes from the session cookie, verified like in every other service: RS256, signature,
 * expiry and issuer (identity and permissions design). Never from a URL, a body or a header (R-06).
 *
 * This only reads the token. Operations that must refuse a disabled account at once (session
 * renewal, current account) also read the account from the database.
 */
@Component
public class TokenCallerProvider implements CallerProvider {

	private final TokenService tokens;

	public TokenCallerProvider(TokenService tokens) {
		this.tokens = tokens;
	}

	@Override
	public Optional<Caller> current(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (SessionCookies.NAME.equals(cookie.getName())) {
				return tokens.verify(cookie.getValue())
						.map(claims -> new Caller(claims.accountId(), claims.admin(), claims.profileId()));
			}
		}
		return Optional.empty();
	}

}
