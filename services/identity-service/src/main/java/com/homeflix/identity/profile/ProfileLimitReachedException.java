package com.homeflix.identity.profile;

/** A sixth profile: answered 409 {@code profile_limit_reached}. */
public class ProfileLimitReachedException extends RuntimeException {

	public ProfileLimitReachedException() {
		super("This account already has the maximum number of profiles.");
	}

}
