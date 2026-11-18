package com.homeflix.identity.web;

/**
 * No valid session: no token, an invalid or expired token, or an account that was disabled
 * or deleted since the token was issued (R-10). Answered 401 {@code unauthenticated}.
 */
public class UnauthenticatedException extends RuntimeException {

	public UnauthenticatedException() {
		super("Please sign in.");
	}

}
