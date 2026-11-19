package com.homeflix.identity.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /profiles} and {@code PUT /profiles/{profileId}}. Validated on the server
 * (SEC-01). The name is trimmed here, as the profile table requires.
 */
public record ProfileRequest(

		@NotBlank(message = "Enter a profile name.")
		@Size(max = ProfileRequest.NAME_MAX_LENGTH, message = "The name can have at most {max} characters.")
		String name) {

	public static final int NAME_MAX_LENGTH = 30;

	public ProfileRequest {
		if (name != null) {
			name = name.strip();
		}
	}

}
