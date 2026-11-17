package com.homeflix.identity.session;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Sign-in body. Validated on the server (SEC-01). The email is trimmed and lower-cased,
 * as stored in the account table; the password is kept exactly as typed.
 */
public record Credentials(

		@NotBlank(message = "Enter your email address.")
		@Email(message = "Enter a valid email address.")
		@Size(max = 254, message = "The email address is too long.")
		String email,

		@NotEmpty(message = "Enter your password.")
		@Size(max = 72, message = "The password is too long.")
		String password) {

	public Credentials {
		if (email != null) {
			email = email.strip().toLowerCase(Locale.ROOT);
		}
	}

	@Override
	public String toString() {
		return "Credentials[email=" + email + ", password=***]";
	}

}
