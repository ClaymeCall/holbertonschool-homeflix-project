package com.homeflix.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading the private key file (IDENTITY_JWT_PRIVATE_KEY_FILE). Fast unit tests, no Spring context.
 */
class SessionKeysTests {

	@TempDir
	Path tempDir;

	@Test
	void pemKeyFileIsReadAndPublicKeyIsDerived() {
		SessionKeys keys = new SessionKeys(TestKeys.writeTemporaryKeyFile(TestKeys.newPrivateKey(2048)));

		assertThat(keys.privateKey().getModulus().bitLength()).isEqualTo(2048);
		assertThat(keys.publicKey().getModulus()).isEqualTo(keys.privateKey().getModulus());
		assertThat(keys.publicKey().getPublicExponent()).isEqualTo(BigInteger.valueOf(65537));
	}

	@Test
	void oneLineBase64KeyIsAccepted() {
		assertThat(SessionKeys.fromKeyText(TestKeys.newPrivateKey(2048)).privateKey()).isNotNull();
	}

	@Test
	void missingSettingStopsTheService() {
		assertThatThrownBy(() -> new SessionKeys(""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("IDENTITY_JWT_PRIVATE_KEY_FILE is not set");
	}

	@Test
	void missingFileStopsTheService() {
		String path = tempDir.resolve("absent.pem").toString();

		assertThatThrownBy(() -> new SessionKeys(path))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("does not exist");
	}

	@Test
	void invalidKeyIsRejectedWithoutPrintingIt() throws IOException {
		Path file = Files.writeString(tempDir.resolve("bad.pem"), "bm90LWEta2V5");

		assertThatThrownBy(() -> new SessionKeys(file.toString()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("not a valid RSA key")
				.hasMessageNotContaining("bm90LWEta2V5");
	}

	@Test
	void pkcs1KeyIsRejectedWithAHint() {
		assertThatThrownBy(() -> SessionKeys.fromKeyText(
				"-----BEGIN RSA PRIVATE KEY-----\nabc\n-----END RSA PRIVATE KEY-----"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PKCS#1");
	}

	@Test
	void shortKeyIsRejected() {
		assertThatThrownBy(() -> SessionKeys.fromKeyText(TestKeys.newPrivateKey(1024)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("2048");
	}

}
