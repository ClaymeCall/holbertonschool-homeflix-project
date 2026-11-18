package com.homeflix.acquisition.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Verifies the session token signed by the identity service, with its public key only
 * (ADR-0009, docs/architecture/identity-and-permissions.md, "Validation in each service").
 * This service never reads the identity service's tables and never calls it to check a request
 * (ACC-07). It does not trust the gateway either: every request is checked here (R-12, ADR-0013).
 *
 * The public key file is given by JWT_PUBLIC_KEY_FILE. The service refuses to start without it.
 */
public class SessionTokenVerifier {

	static final String ISSUER = "homeflix-identity";
	static final Duration CLOCK_SKEW = Duration.ofSeconds(5);
	static final int MIN_KEY_BITS = 2048;
	private static final String VARIABLE = "JWT_PUBLIC_KEY_FILE";

	private final RSASSAVerifier verifier;
	private final Clock clock;

	public SessionTokenVerifier(RSAPublicKey publicKey, Clock clock) {
		this.verifier = new RSASSAVerifier(publicKey);
		this.clock = clock;
	}

	/** Reads the key from a file path, or from {@code classpath:...} (tests). */
	public static SessionTokenVerifier fromKeyFile(String keyFile, Clock clock) {
		return new SessionTokenVerifier(parsePublicKey(readKeyFile(keyFile)), clock);
	}

	/**
	 * The caller stated by a valid token: RS256 only, signed with the identity service's key,
	 * issued by it, not expired. Anything else, including a malformed token, gives an empty result.
	 */
	public Optional<Caller> verify(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			// Only RS256: refuses "none", HS256 and any other algorithm (algorithm confusion).
			if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
				return Optional.empty();
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Date expiry = claims.getExpirationTime();
			if (expiry == null || !clock.instant().isBefore(expiry.toInstant().plus(CLOCK_SKEW))) {
				return Optional.empty();
			}
			Boolean admin = claims.getBooleanClaim("admin");
			if (!ISSUER.equals(claims.getIssuer()) || claims.getSubject() == null || admin == null) {
				return Optional.empty();
			}
			String profileId = claims.getStringClaim("profileId");
			return Optional.of(new Caller(
					UUID.fromString(claims.getSubject()),
					admin,
					profileId == null ? null : UUID.fromString(profileId)));
		}
		catch (ParseException | JOSEException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	static String readKeyFile(String keyFile) {
		if (keyFile == null || keyFile.isBlank()) {
			throw new IllegalStateException(VARIABLE + " is not set. Generate the key pair as explained in .env.example.");
		}
		String location = keyFile.strip();
		if (location.startsWith("classpath:")) {
			String resource = location.substring("classpath:".length());
			try (InputStream in = SessionTokenVerifier.class.getClassLoader().getResourceAsStream(resource)) {
				if (in == null) {
					throw new IllegalStateException("The public key " + location + " (" + VARIABLE + ") does not exist.");
				}
				return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
			}
			catch (IOException ex) {
				throw new IllegalStateException("The public key " + location + " (" + VARIABLE + ") cannot be read.");
			}
		}
		Path path = Path.of(location);
		if (!Files.isRegularFile(path)) {
			throw new IllegalStateException("The public key file " + path + " (" + VARIABLE + ") does not exist.");
		}
		try {
			return Files.readString(path, StandardCharsets.US_ASCII);
		}
		catch (IOException ex) {
			throw new IllegalStateException("The public key file " + path + " (" + VARIABLE
					+ ") cannot be read. Check its permissions.");
		}
	}

	/** Accepts a PEM public key ("BEGIN PUBLIC KEY"), as written by {@code openssl pkey -pubout}. */
	static RSAPublicKey parsePublicKey(String pem) {
		String base64 = pem
				.replace("-----BEGIN PUBLIC KEY-----", "")
				.replace("-----END PUBLIC KEY-----", "")
				.replaceAll("\\s", "");
		PublicKey key;
		try {
			key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
		}
		catch (IllegalArgumentException | GeneralSecurityException ex) {
			throw new IllegalStateException("The public key is not a valid RSA public key in PEM format. "
					+ "Generate it as explained in .env.example.");
		}
		if (!(key instanceof RSAPublicKey rsaKey) || rsaKey.getModulus().bitLength() < MIN_KEY_BITS) {
			throw new IllegalStateException("The public key must be an RSA key of at least 2048 bits.");
		}
		return rsaKey;
	}

}
