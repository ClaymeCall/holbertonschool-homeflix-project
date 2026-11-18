package com.homeflix.acquisition.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * How the acquisition service calls the library's internal endpoints (consistency rules, ADR-0022).
 * The service refuses to start without the token, as ADR-0022 requires.
 *
 * @param baseUrl the library service, on the private backend network (never through the gateway)
 * @param serviceToken the secret this service sends in {@code X-Service-Token}; the library holds the same
 *        value for this caller only
 */
@Validated
@ConfigurationProperties("homeflix.library")
public record LibraryProperties(
		@NotBlank String baseUrl,
		@NotBlank(message = "ACQUISITION_TO_LIBRARY_TOKEN is not set. Generate one as explained in .env.example.")
		@Size(min = 32, message = "ACQUISITION_TO_LIBRARY_TOKEN must be at least 32 characters long.")
		String serviceToken) {

	@Override
	public String toString() {
		return "LibraryProperties[baseUrl=" + baseUrl + ", serviceToken=***]";
	}

}
