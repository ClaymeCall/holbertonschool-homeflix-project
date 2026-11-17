package com.homeflix.identity.session;

/**
 * Unknown email, wrong password or disabled account. One single answer for the three cases,
 * so a failed sign-in never reveals which accounts exist (ACC-10, error model).
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid credentials.");
	}

}
