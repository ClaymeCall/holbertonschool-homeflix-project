package com.homeflix.identity.profile;

/** A name already used by another profile of the account: answered 409 {@code profile_name_taken}. */
public class ProfileNameTakenException extends RuntimeException {

	public ProfileNameTakenException() {
		super("A profile with this name already exists.");
	}

}
