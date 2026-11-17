package com.homeflix.identity.account;

import java.util.UUID;

/**
 * A row of the account table. Internal to the identity service: never returned as is.
 */
public record AccountRecord(UUID id, String email, String passwordHash, boolean admin, boolean disabled) {

	@Override
	public String toString() {
		// Never print the password hash, even in debug logs.
		return "AccountRecord[id=" + id + ", admin=" + admin + ", disabled=" + disabled + "]";
	}

}
