package com.homeflix.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nimbusds.jwt.SignedJWT;

/**
 * Issuing and verifying session tokens (ADR-0009, R-10, TST-02). Unit tests, no Spring context.
 */
class TokenServiceTests {

	private static final SessionKeys KEYS = new SessionKeys(TestKeys.newPrivateKey(2048));
	private static final Duration LIFETIME = Duration.ofMinutes(15);
	private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

	private final UUID accountId = UUID.randomUUID();
	private final UUID profileId = UUID.randomUUID();

	@Test
	void validTokenGivesBackItsClaims() {
		TokenService tokens = tokensAt(NOW);
		String token = tokens.issue(new SessionClaims(accountId, true, profileId));

		assertThat(tokens.verify(token)).contains(new SessionClaims(accountId, true, profileId));
	}

	@Test
	void tokenCarriesTheDocumentedClaims() throws Exception {
		String token = tokensAt(NOW).issue(new SessionClaims(accountId, false, null));
		SignedJWT jwt = SignedJWT.parse(token);

		assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
		assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(accountId.toString());
		assertThat(jwt.getJWTClaimsSet().getBooleanClaim("admin")).isFalse();
		assertThat(jwt.getJWTClaimsSet().getClaim("profileId")).isNull();
		assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(NOW.plus(LIFETIME));
	}

	@Test
	void tokenIsValidJustBeforeExpiryAndRefusedAfter() {
		String token = tokensAt(NOW).issue(new SessionClaims(accountId, false, null));

		assertThat(tokensAt(NOW.plus(LIFETIME).minusSeconds(1)).verify(token)).isPresent();
		assertThat(tokensAt(NOW.plus(LIFETIME)).verify(token)).isEmpty();
		assertThat(tokensAt(NOW.plus(Duration.ofHours(1))).verify(token)).isEmpty();
	}

	@Test
	void tokenSignedWithAnotherKeyIsRefused() {
		SessionKeys otherKeys = new SessionKeys(TestKeys.newPrivateKey(2048));
		String forged = new TokenService(otherKeys, fixedClock(NOW), LIFETIME)
				.issue(new SessionClaims(accountId, true, null));

		assertThat(tokensAt(NOW).verify(forged)).isEmpty();
	}

	@Test
	void modifiedPayloadIsRefused() {
		String token = tokensAt(NOW).issue(new SessionClaims(accountId, false, null));
		String[] parts = token.split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
				.replace("\"admin\":false", "\"admin\":true");
		String tampered = parts[0] + "." + base64Url(payload) + "." + parts[2];

		assertThat(tokensAt(NOW).verify(tampered)).isEmpty();
	}

	@Test
	void unsignedTokenIsRefused() {
		long exp = NOW.plus(LIFETIME).getEpochSecond();
		String unsigned = base64Url("{\"alg\":\"none\"}") + "."
				+ base64Url("{\"iss\":\"homeflix-identity\",\"sub\":\"" + accountId + "\",\"admin\":true,\"exp\":" + exp + "}")
				+ ".";

		assertThat(tokensAt(NOW).verify(unsigned)).isEmpty();
	}

	@Test
	void garbageIsRefused() {
		TokenService tokens = tokensAt(NOW);

		assertThat(tokens.verify(null)).isEmpty();
		assertThat(tokens.verify("")).isEmpty();
		assertThat(tokens.verify("not-a-token")).isEmpty();
		assertThat(tokens.verify("a.b.c")).isEmpty();
	}

	private static TokenService tokensAt(Instant instant) {
		return new TokenService(KEYS, fixedClock(instant), LIFETIME);
	}

	private static Clock fixedClock(Instant instant) {
		return Clock.fixed(instant, ZoneOffset.UTC);
	}

	private static String base64Url(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
