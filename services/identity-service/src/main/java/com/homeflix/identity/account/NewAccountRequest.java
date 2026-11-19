package com.homeflix.identity.account;

import java.util.Locale;

import com.homeflix.identity.setup.SetupRequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /accounts} (ACC-09). Same rules as the first-run setup (SEC-01): the email
 * is trimmed and lower-cased as the account table requires, the password is kept exactly as typed.
 * {@code admin} defaults to false, so a forgotten value never grants administrator rights.
 */
public record NewAccountRequest(

		@NotBlank(message = "Enter an email address.")
		@Email(message = "Enter a valid email address.")
		@Size(max = 254, message = "The email address is too long.")
		String email,

		@NotNull(message = "Choose a password.")
		@Size(min = SetupRequest.PASSWORD_MIN_LENGTH,
				message = "The password must be at least {min} characters long.")
		String password,

		Boolean admin) {

	public NewAccountRequest {
		if (email != null) {
			email = email.strip().toLowerCase(Locale.ROOT);
		}
		if (admin == null) {
			admin = false;
		}
	}

	@Override
	public String toString() {
		// Never print the password, even in debug logs.
		return "NewAccountRequest[email=" + email + ", password=***, admin=" + admin + "]";
	}

}
