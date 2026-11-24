package com.homeflix.acquisition.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * The service refuses to start without its service token and its torrent client login (ADR-0022,
 * ADR-0023), and never prints them.
 */
class SettingsTests {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void aMissingOrShortServiceTokenIsRefusedWithAHint() {
		assertThat(messages(new LibraryProperties("http://library-service:8082", "")))
				.contains("ACQUISITION_TO_LIBRARY_TOKEN is not set. Generate one as explained in .env.example.");
		assertThat(messages(new LibraryProperties("http://library-service:8082", "too-short")))
				.contains("ACQUISITION_TO_LIBRARY_TOKEN must be at least 32 characters long.");
		assertThat(messages(new LibraryProperties("http://library-service:8082", "a".repeat(32)))).isEmpty();
	}

	@Test
	void aMissingTorrentClientLoginIsRefused() {
		assertThat(messages(new QbittorrentProperties("http://qbittorrent:8080", "", "")))
				.contains("QBITTORRENT_USERNAME is not set. Set it in .env.", "QBITTORRENT_PASSWORD is not set. Set it in .env.");
	}

	@Test
	void secretsAreNeverPrinted() {
		assertThat(new LibraryProperties("http://library-service:8082", "secret-token-value").toString())
				.doesNotContain("secret-token-value");
		assertThat(new QbittorrentProperties("http://qbittorrent:8080", "homeflix", "secret-password").toString())
				.doesNotContain("secret-password");
	}

	private <T> java.util.List<String> messages(T settings) {
		return validator.validate(settings).stream().map(ConstraintViolation::getMessage).toList();
	}

}
