package com.homeflix.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

/**
 * Reading JWT_PRIVATE_KEY (OPS-03). Fast unit tests, no Spring context.
 */
class SessionKeysTests {

	@Test
	void base64KeyIsAcceptedAndPublicKeyIsDerived() {
		SessionKeys keys = new SessionKeys(TestKeys.newPrivateKey(2048));

		assertThat(keys.publicKey().getModulus()).isEqualTo(keys.privateKey().getModulus());
		assertThat(keys.publicKey().getPublicExponent()).isEqualTo(BigInteger.valueOf(65537));
	}

	@Test
	void pemKeyIsAccepted() {
		String base64 = TestKeys.newPrivateKey(2048);
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ base64.replaceAll("(.{64})", "$1\n")
				+ "\n-----END PRIVATE KEY-----\n";

		assertThat(new SessionKeys(pem).privateKey().getModulus().bitLength()).isEqualTo(2048);
	}

	@Test
	void missingKeyStopsTheService() {
		assertThatThrownBy(() -> new SessionKeys(""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_PRIVATE_KEY is not set");
	}

	@Test
	void invalidKeyIsRejectedWithoutPrintingIt() {
		assertThatThrownBy(() -> new SessionKeys("bm90LWEta2V5"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("not a valid RSA private key")
				.hasMessageNotContaining("bm90LWEta2V5");
	}

	@Test
	void pkcs1KeyIsRejectedWithAHint() {
		assertThatThrownBy(() -> new SessionKeys("-----BEGIN RSA PRIVATE KEY-----\nabc\n-----END RSA PRIVATE KEY-----"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PKCS#1");
	}

	@Test
	void shortKeyIsRejected() {
		assertThatThrownBy(() -> new SessionKeys(TestKeys.newPrivateKey(1024)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("2048");
	}

}
