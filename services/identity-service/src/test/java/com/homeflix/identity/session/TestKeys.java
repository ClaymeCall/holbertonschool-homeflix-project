package com.homeflix.identity.session;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * RSA keys generated when the tests start, never committed (R-11).
 */
public final class TestKeys {

	/** File of the key the application under test signs with, deleted when the JVM stops. */
	public static final String SESSION_PRIVATE_KEY_FILE = writeTemporaryKeyFile(newPrivateKey(2048));

	private TestKeys() {
	}

	/** A PKCS#8 RSA private key as one line of base64. */
	public static String newPrivateKey(int bits) {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(bits);
			return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** The same key as PEM, as written by {@code openssl genpkey}. */
	public static String toPem(String base64Key) {
		return "-----BEGIN PRIVATE KEY-----\n"
				+ base64Key.replaceAll("(.{64})", "$1\n").strip()
				+ "\n-----END PRIVATE KEY-----\n";
	}

	public static String writeTemporaryKeyFile(String base64Key) {
		try {
			Path file = Files.createTempFile("homeflix-test-key-", ".pem");
			file.toFile().deleteOnExit();
			Files.writeString(file, toPem(base64Key), StandardCharsets.US_ASCII);
			return file.toString();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
