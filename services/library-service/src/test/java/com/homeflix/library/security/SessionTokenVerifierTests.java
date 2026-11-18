package com.homeflix.library.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Token verification with the public key only (G5, TST-02). Unit tests, no Spring context.
 */
class SessionTokenVerifierTests {

	private final SessionTokenVerifier verifier =
			new SessionTokenVerifier(TestTokens.identityPublicKey(), Clock.systemUTC());
	private final UUID accountId = UUID.randomUUID();
	private final UUID profileId = UUID.randomUUID();

	@Test
	void validUserTokenGivesTheCaller() {
		assertThat(verifier.verify(TestTokens.user(accountId, profileId)))
				.contains(new Caller(accountId, false, profileId));
	}

	@Test
	void validAdminTokenGivesAnAdministratorWithoutProfile() {
		assertThat(verifier.verify(TestTokens.admin(accountId)))
				.contains(new Caller(accountId, true, null));
	}

	@Test
	void expiredTokenIsRefused() {
		String expired = TestTokens.token(TestTokens.IDENTITY_KEYS, "homeflix-identity", accountId, true, null,
				Instant.now().minus(Duration.ofMinutes(20)), Duration.ofMinutes(15));

		assertThat(verifier.verify(expired)).isEmpty();
	}

	@Test
	void tokenSignedWithAnotherKeyIsRefused() {
		String forged = TestTokens.token(TestTokens.newKeyPair(), "homeflix-identity", accountId, true, null,
				Instant.now(), Duration.ofMinutes(15));

		assertThat(verifier.verify(forged)).isEmpty();
	}

	@Test
	void tokenFromAnotherIssuerIsRefused() {
		String other = TestTokens.token(TestTokens.IDENTITY_KEYS, "someone-else", accountId, true, null,
				Instant.now(), Duration.ofMinutes(15));

		assertThat(verifier.verify(other)).isEmpty();
	}

	@Test
	void modifiedPayloadIsRefused() {
		String[] parts = TestTokens.user(accountId, profileId).split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
				.replace("\"admin\":false", "\"admin\":true");
		String tampered = parts[0] + "." + base64Url(payload) + "." + parts[2];

		assertThat(verifier.verify(tampered)).isEmpty();
	}

	@Test
	void unsignedTokenIsRefused() {
		long exp = Instant.now().plus(Duration.ofMinutes(15)).getEpochSecond();
		String unsigned = base64Url("{\"alg\":\"none\"}") + "."
				+ base64Url("{\"iss\":\"homeflix-identity\",\"sub\":\"" + accountId + "\",\"admin\":true,\"exp\":" + exp + "}")
				+ ".";

		assertThat(verifier.verify(unsigned)).isEmpty();
	}

	@Test
	void garbageIsRefused() {
		assertThat(verifier.verify(null)).isEmpty();
		assertThat(verifier.verify("")).isEmpty();
		assertThat(verifier.verify("a.b.c")).isEmpty();
	}

	@Test
	void publicKeyIsReadFromAFile() {
		String file = TestTokens.writeTemporaryPem(TestTokens.toPem(TestTokens.identityPublicKey()));

		SessionTokenVerifier fromFile = SessionTokenVerifier.fromKeyFile(file, Clock.systemUTC());

		assertThat(fromFile.verify(TestTokens.admin(accountId))).isPresent();
	}

	@Test
	void missingKeySettingStopsTheService() {
		assertThatThrownBy(() -> SessionTokenVerifier.fromKeyFile("", Clock.systemUTC()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_PUBLIC_KEY_FILE is not set");
	}

	@Test
	void missingKeyFileStopsTheService() {
		assertThatThrownBy(() -> SessionTokenVerifier.fromKeyFile("/nowhere/jwt-public.pem", Clock.systemUTC()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("does not exist");
	}

	@Test
	void invalidKeyIsRejected() {
		String file = TestTokens.writeTemporaryPem("-----BEGIN PUBLIC KEY-----\nbm90LWEta2V5\n-----END PUBLIC KEY-----\n");

		assertThatThrownBy(() -> SessionTokenVerifier.fromKeyFile(file, Clock.systemUTC()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("not a valid RSA public key");
	}

	private static String base64Url(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
