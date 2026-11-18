package com.homeflix.acquisition.web;

/** Signed in, but the role does not allow the operation: answered 403 {@code forbidden}. */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException() {
		super("You do not have permission to do this.");
	}

}
