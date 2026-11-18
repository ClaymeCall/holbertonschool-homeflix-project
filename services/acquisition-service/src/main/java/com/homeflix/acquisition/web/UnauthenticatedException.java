package com.homeflix.acquisition.web;

/** No valid session: answered 401 {@code unauthenticated}. */
public class UnauthenticatedException extends RuntimeException {

	public UnauthenticatedException() {
		super("Please sign in.");
	}

}
