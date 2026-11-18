package com.homeflix.identity.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RSA key pair used to sign session tokens (RS256, ADR-0009).
 *
 * The private key is read from a file whose path is given by IDENTITY_JWT_PRIVATE_KEY_FILE
 * (identity and permissions design, "Keys and secrets"). In Docker the file is a mounted
 * secret, never part of the repository or the image. The public key is derived from it; the
 * other services receive the public key only (G5). The service refuses to start without a
 * valid key, and error messages never contain the key.
 */
@Component
public class SessionKeys {

	static final int MIN_KEY_BITS = 2048;
	private static final String VARIABLE = "IDENTITY_JWT_PRIVATE_KEY_FILE";

	private final RSAPrivateCrtKey privateKey;
	private final RSAPublicKey publicKey;

	@Autowired
	public SessionKeys(@Value("${homeflix.session.private-key-file:}") String keyFile) {
		this(parsePrivateKey(readKeyFile(keyFile)));
	}

	private SessionKeys(RSAPrivateCrtKey privateKey) {
		this.privateKey = privateKey;
		this.publicKey = derivePublicKey(privateKey);
	}

	/** For tests and tools: a key given as text (PEM or one line of base64) instead of a file. */
	public static SessionKeys fromKeyText(String keyText) {
		return new SessionKeys(parsePrivateKey(keyText));
	}

	public RSAPrivateCrtKey privateKey() {
		return privateKey;
	}

	public RSAPublicKey publicKey() {
		return publicKey;
	}

	static String readKeyFile(String keyFile) {
		if (keyFile == null || keyFile.isBlank()) {
			throw new IllegalStateException(
					VARIABLE + " is not set. Generate a key pair as explained in .env.example.");
		}
		Path path = Path.of(keyFile.strip());
		if (!Files.isRegularFile(path)) {
			throw new IllegalStateException("The private key file " + path + " (" + VARIABLE + ") does not exist.");
		}
		if (!Files.isReadable(path)) {
			throw new IllegalStateException("The private key file " + path + " (" + VARIABLE
					+ ") cannot be read by the service. Check its permissions.");
		}
		try {
			return Files.readString(path, StandardCharsets.US_ASCII);
		}
		catch (IOException ex) {
			throw new IllegalStateException("The private key file " + path + " (" + VARIABLE + ") cannot be read.");
		}
	}

	/** Accepts a PKCS#8 RSA key as PEM ("BEGIN PRIVATE KEY") or as one line of base64. */
	static RSAPrivateCrtKey parsePrivateKey(String keyText) {
		if (keyText == null || keyText.isBlank()) {
			throw new IllegalStateException("The private key is empty.");
		}
		if (keyText.contains("BEGIN RSA PRIVATE KEY")) {
			throw new IllegalStateException(
					"The private key is in PKCS#1 format. Use a PKCS#8 key, generated as explained in .env.example.");
		}
		String base64 = keyText
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");

		PrivateKey key;
		try {
			byte[] der = Base64.getDecoder().decode(base64);
			key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
		}
		catch (IllegalArgumentException | GeneralSecurityException ex) {
			throw new IllegalStateException(
					"The private key is not a valid RSA key in PKCS#8 format. Generate one as explained in .env.example.");
		}
		if (!(key instanceof RSAPrivateCrtKey rsaKey)) {
			throw new IllegalStateException("The private key must be a complete RSA private key.");
		}
		if (rsaKey.getModulus().bitLength() < MIN_KEY_BITS) {
			throw new IllegalStateException("The private key must be an RSA key of at least 2048 bits.");
		}
		return rsaKey;
	}

	private static RSAPublicKey derivePublicKey(RSAPrivateCrtKey privateKey) {
		try {
			RSAPublicKeySpec spec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
			return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not derive the public key from the private key.");
		}
	}

}
