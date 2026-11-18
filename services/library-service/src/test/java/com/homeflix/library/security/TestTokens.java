package com.homeflix.library.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Makes session tokens like the identity service does, with a key pair generated for this test
 * run only (never committed, R-11).
 */
public final class TestTokens {

	/** Stands for the identity service's key pair. */
	public static final KeyPair IDENTITY_KEYS = newKeyPair();

	private TestTokens() {
	}

	public static KeyPair newKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static RSAPublicKey identityPublicKey() {
		return (RSAPublicKey) IDENTITY_KEYS.getPublic();
	}

	/** A valid token of an ordinary user with an active profile. */
	public static String user(UUID accountId, UUID profileId) {
		return token(IDENTITY_KEYS, "homeflix-identity", accountId, false, profileId, Instant.now(), Duration.ofMinutes(15));
	}

	/** A valid token of an administrator, without an active profile. */
	public static String admin(UUID accountId) {
		return token(IDENTITY_KEYS, "homeflix-identity", accountId, true, null, Instant.now(), Duration.ofMinutes(15));
	}

	public static String token(KeyPair keys, String issuer, UUID accountId, boolean admin, UUID profileId,
			Instant issuedAt, Duration lifetime) {
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject(accountId.toString())
				.claim("admin", admin)
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(issuedAt.plus(lifetime)));
		if (profileId != null) {
			claims.claim("profileId", profileId.toString());
		}
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims.build());
		try {
			jwt.sign(new RSASSASigner(keys.getPrivate()));
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
		return jwt.serialize();
	}

	/** The public key as written by {@code openssl pkey -pubout}. */
	public static String toPem(PublicKey key) {
		return "-----BEGIN PUBLIC KEY-----\n"
				+ Base64.getEncoder().encodeToString(key.getEncoded()).replaceAll("(.{64})", "$1\n").strip()
				+ "\n-----END PUBLIC KEY-----\n";
	}

	public static String writeTemporaryPem(String pem) {
		try {
			Path file = Files.createTempFile("homeflix-test-public-", ".pem");
			file.toFile().deleteOnExit();
			Files.writeString(file, pem, StandardCharsets.US_ASCII);
			return file.toString();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
