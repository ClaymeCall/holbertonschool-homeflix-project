package com.homeflix.identity.session;

/**
 * No valid session: no token, an invalid or expired token, or an account that was disabled
 * or deleted since the token was issued (R-10).
 */
public class UnauthenticatedException extends RuntimeException {

	public UnauthenticatedException() {
		super("No valid session.");
	}

}
