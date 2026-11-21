package com.homeflix.library.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Poster address rules (CAT-11, SEC-12, ADR-0018, R-17, R-33). */
class PosterPolicyTest {

	private final PosterPolicy policy = new PosterPolicy(List.of("upload.wikimedia.org", " Images.Example.ORG ", ""));

	@ParameterizedTest
	@ValueSource(strings = {
			"https://upload.wikimedia.org/wikipedia/commons/a/a9/poster.jpg",
			"https://UPLOAD.wikimedia.org/poster.jpg",
			"HTTPS://upload.wikimedia.org/poster.jpg",
			"https://images.example.org/p.png?size=large#top",
			"https://upload.wikimedia.org:443/poster.jpg" })
	void acceptsHttpsOnAnAllowedHost(String address) {
		assertThat(policy.refusal(address)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://upload.wikimedia.org/poster.jpg",
			"ftp://upload.wikimedia.org/poster.jpg",
			"javascript:alert(1)",
			"data:image/png;base64,AAAA",
			"file:///etc/passwd",
			"//upload.wikimedia.org/poster.jpg",
			"upload.wikimedia.org/poster.jpg" })
	void refusesAnythingButHttps(String address) {
		assertThat(policy.refusal(address)).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://evil.example.com/poster.jpg",
			"https://wikimedia.org/poster.jpg",
			"https://sub.upload.wikimedia.org/poster.jpg",
			"https://upload.wikimedia.org.evil.com/poster.jpg",
			"https://evilupload.wikimedia.org/poster.jpg",
			"https://upload.wikimedia.org./poster.jpg",
			"https://localhost/poster.jpg",
			"https://127.0.0.1/poster.jpg",
			"https://[::1]/poster.jpg",
			"https://169.254.169.254/latest/meta-data" })
	void refusesAHostOutsideTheList(String address) {
		assertThat(policy.refusal(address)).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://upload.wikimedia.org@evil.com/poster.jpg",
			"https://user:pw@upload.wikimedia.org/poster.jpg",
			"https://evil.com\\@upload.wikimedia.org/poster.jpg",
			"https://upload.wikimedia.org:8443/poster.jpg",
			"https://upload.wikimedia.org:80/poster.jpg",
			"https://upload .wikimedia.org/poster.jpg",
			"https://",
			"https:///poster.jpg",
			"not an address",
			"https://upload.wikimedia.org/a b.jpg" })
	void refusesMalformedOrTrickyAddresses(String address) {
		assertThat(policy.refusal(address)).isPresent();
	}

	@Test
	void refusesAnAddressThatIsTooLong() {
		assertThat(policy.refusal("https://upload.wikimedia.org/" + "a".repeat(2048))).contains("The address is too long.");
	}

	@Test
	void theMessageNamesTheHostButNeverTheWholeAddress() {
		assertThat(policy.refusal("https://evil.example.com/secret?token=1").orElseThrow())
				.contains("evil.example.com").doesNotContain("token");
		assertThat(policy.refusal("http://upload.wikimedia.org/p.jpg").orElseThrow()).contains("https://");
	}

	@Test
	void anEmptyListRefusesEveryPoster() {
		assertThat(new PosterPolicy(List.of()).refusal("https://upload.wikimedia.org/p.jpg")).isPresent();
	}

	@Test
	void theListIsNormalisedToLowerCaseWithoutBlanks() {
		assertThat(policy.allowedHosts()).containsExactlyInAnyOrder("upload.wikimedia.org", "images.example.org");
	}

	@Test
	void aMalformedListEntryStopsTheServiceFromStarting() {
		for (String bad : new String[] { "https://upload.wikimedia.org", "*.wikimedia.org", "upload.wikimedia.org/x", "localhost" }) {
			assertThatThrownBy(() -> new PosterPolicy(List.of(bad))).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("POSTER_ALLOWED_HOSTS");
		}
	}

}
