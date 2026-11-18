package com.homeflix.identity.setup;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of the first-run setup request (ACC-01). Validated on the server (SEC-01).
 * The email is trimmed and lower-cased here, as the account table requires (G1).
 * The password is kept exactly as typed.
 */
public record SetupRequest(

		@NotBlank(message = "Enter an email address.")
		@Email(message = "Enter a valid email address.")
		@Size(max = 254, message = "The email address is too long.")
		String email,

		@NotNull(message = "Choose a password.")
		@Size(min = SetupRequest.PASSWORD_MIN_LENGTH,
				message = "The password must be at least {min} characters long.")
		String password) {

	public static final int PASSWORD_MIN_LENGTH = 12;

	public SetupRequest {
		if (email != null) {
			email = email.strip().toLowerCase(Locale.ROOT);
		}
	}

	@Override
	public String toString() {
		// Never print the password, even in debug logs.
		return "SetupRequest[email=" + email + ", password=***]";
	}

}
