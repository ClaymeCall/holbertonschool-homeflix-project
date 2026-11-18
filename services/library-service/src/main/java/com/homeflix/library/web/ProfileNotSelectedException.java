package com.homeflix.library.web;

/** Signed in, but no profile is chosen yet: answered 403 {@code profile_not_selected}. */
public class ProfileNotSelectedException extends RuntimeException {

	public ProfileNotSelectedException() {
		super("Choose a profile first.");
	}

}
