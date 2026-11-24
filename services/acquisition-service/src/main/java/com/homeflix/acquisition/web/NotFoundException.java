package com.homeflix.acquisition.web;

/** The resource does not exist, or is not the caller's to see: answered 404 {@code not_found}. */
public class NotFoundException extends RuntimeException {

	public NotFoundException() {
		super("This address does not exist.");
	}

}
