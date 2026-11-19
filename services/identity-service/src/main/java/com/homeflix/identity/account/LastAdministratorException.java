package com.homeflix.identity.account;

/**
 * The change would leave no enabled administrator (identity design, "Roles and permissions").
 * Answered 409 {@code last_administrator}.
 */
public class LastAdministratorException extends RuntimeException {

	public LastAdministratorException() {
		super("HomeFlix needs at least one administrator. Make another account an administrator first.");
	}

}
