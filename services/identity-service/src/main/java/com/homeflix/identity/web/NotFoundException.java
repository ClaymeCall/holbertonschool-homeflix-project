package com.homeflix.identity.web;

/**
 * The resource does not exist, or is not visible to the caller: answered 404 {@code not_found},
 * the same answer in both cases so ids cannot be probed (PRF-04).
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException() {
		super("This address does not exist.");
	}

}
