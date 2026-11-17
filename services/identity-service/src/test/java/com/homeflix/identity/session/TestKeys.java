package com.homeflix.identity.session;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * RSA keys generated when the tests start, in the format JWT_PRIVATE_KEY expects
 * (PKCS#8, one line of base64). Never written to disk.
 */
public final class TestKeys {

	/** The key the application under test signs with. */
	public static final String SESSION_PRIVATE_KEY = newPrivateKey(2048);

	private TestKeys() {
	}

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

}
