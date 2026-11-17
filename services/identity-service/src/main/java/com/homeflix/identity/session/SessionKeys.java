package com.homeflix.identity.session;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RSA key pair used to sign session tokens (RS256, ADR-0009).
 *
 * The private key comes from the environment only (JWT_PRIVATE_KEY, OPS-03), never from
 * the repository. The public key is derived from it; the other services will receive
 * the public key only (G5). The service refuses to start without a valid key.
 */
@Component
public class SessionKeys {

	static final int MIN_KEY_BITS = 2048;

	private final RSAPrivateCrtKey privateKey;
	private final RSAPublicKey publicKey;

	public SessionKeys(@Value("${homeflix.session.private-key:}") String configuredKey) {
		this.privateKey = parsePrivateKey(configuredKey);
		this.publicKey = derivePublicKey(privateKey);
	}

	public RSAPrivateCrtKey privateKey() {
		return privateKey;
	}

	public RSAPublicKey publicKey() {
		return publicKey;
	}

	/**
	 * Accepts a PKCS#8 RSA key as one line of base64, or as PEM ("BEGIN PRIVATE KEY").
	 * Error messages never contain the key itself.
	 */
	static RSAPrivateCrtKey parsePrivateKey(String configuredKey) {
		if (configuredKey == null || configuredKey.isBlank()) {
			throw new IllegalStateException(
					"JWT_PRIVATE_KEY is not set. Generate a key as explained in .env.example.");
		}
		if (configuredKey.contains("BEGIN RSA PRIVATE KEY")) {
			throw new IllegalStateException(
					"JWT_PRIVATE_KEY is in PKCS#1 format. Use a PKCS#8 key, generated as explained in .env.example.");
		}
		String base64 = configuredKey
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replace("\\n", "")
				.replaceAll("\\s", "");

		PrivateKey key;
		try {
			byte[] der = Base64.getDecoder().decode(base64);
			key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
		}
		catch (IllegalArgumentException | GeneralSecurityException ex) {
			throw new IllegalStateException(
					"JWT_PRIVATE_KEY is not a valid RSA private key in PKCS#8 format. Generate one as explained in .env.example.");
		}
		if (!(key instanceof RSAPrivateCrtKey rsaKey)) {
			throw new IllegalStateException("JWT_PRIVATE_KEY must be a complete RSA private key.");
		}
		if (rsaKey.getModulus().bitLength() < MIN_KEY_BITS) {
			throw new IllegalStateException("JWT_PRIVATE_KEY must be an RSA key of at least 2048 bits.");
		}
		return rsaKey;
	}

	private static RSAPublicKey derivePublicKey(RSAPrivateCrtKey privateKey) {
		try {
			RSAPublicKeySpec spec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
			return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not derive the public key from JWT_PRIVATE_KEY.");
		}
	}

}
