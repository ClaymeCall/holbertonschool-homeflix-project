package com.homeflix.identity.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The session cookie: {@code homeflix_token=<jwt>; Path=/; HttpOnly; SameSite=Strict}
 * (ADR-0009, docs/api/identity.yaml). HttpOnly keeps the token out of reach of scripts (XSS),
 * SameSite=Strict keeps the browser from sending it on cross-site requests (CSRF, R-09).
 * {@code Secure} is added when HomeFlix is served over HTTPS (issue L8).
 */
@Component
public class SessionCookies {

	public static final String NAME = "homeflix_token";

	private final boolean secure;
	private final TokenService tokens;

	public SessionCookies(@Value("${homeflix.session.cookie-secure:false}") boolean secure, TokenService tokens) {
		this.secure = secure;
		this.tokens = tokens;
	}

	/** Holds the token, and expires with it. */
	public ResponseCookie session(String token) {
		return base(token).maxAge(tokens.lifetime()).build();
	}

	/** Empty value and Max-Age=0: the browser deletes the cookie. */
	public ResponseCookie cleared() {
		return base("").maxAge(0).build();
	}

	private ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(NAME, value)
				.path("/")
				.httpOnly(true)
				.secure(secure)
				.sameSite("Strict");
	}

}
