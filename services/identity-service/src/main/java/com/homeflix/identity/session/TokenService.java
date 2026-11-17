package com.homeflix.identity.session;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Issues and verifies session tokens: short-lived JWTs signed with RS256 (ADR-0009, R-10).
 * There is no revocation list: a token is valid until it expires, so the lifetime is short
 * and renewal goes through the identity service, which checks the account each time.
 */
@Component
public class TokenService {

	static final String ISSUER = "homeflix-identity";
	static final String ADMIN_CLAIM = "admin";
	static final String PROFILE_CLAIM = "profileId";

	private final RSASSASigner signer;
	private final RSASSAVerifier verifier;
	private final Clock clock;
	private final Duration lifetime;

	public TokenService(SessionKeys keys, Clock clock,
			@Value("${homeflix.session.token-lifetime:15m}") Duration lifetime) {
		this.signer = new RSASSASigner(keys.privateKey());
		this.verifier = new RSASSAVerifier(keys.publicKey());
		this.clock = clock;
		this.lifetime = lifetime;
	}

	public Duration lifetime() {
		return lifetime;
	}

	public String issue(SessionClaims session) {
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
				.issuer(ISSUER)
				.subject(session.accountId().toString())
				.claim(ADMIN_CLAIM, session.admin())
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(lifetime)))
				.jwtID(UUID.randomUUID().toString());
		if (session.profileId() != null) {
			claims.claim(PROFILE_CLAIM, session.profileId().toString());
		}

		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
				claims.build());
		try {
			jwt.sign(signer);
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("Could not sign the session token.", ex);
		}
		return jwt.serialize();
	}

	/**
	 * Returns the claims of a valid token: RS256, signed with our key, issued by the identity
	 * service and not expired. Anything else, including a malformed token, gives an empty result.
	 */
	public Optional<SessionClaims> verify(String token) {
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
			if (expiry == null || !clock.instant().isBefore(expiry.toInstant())) {
				return Optional.empty();
			}
			Boolean admin = claims.getBooleanClaim(ADMIN_CLAIM);
			if (!ISSUER.equals(claims.getIssuer()) || claims.getSubject() == null || admin == null) {
				return Optional.empty();
			}
			String profileId = claims.getStringClaim(PROFILE_CLAIM);
			return Optional.of(new SessionClaims(
					UUID.fromString(claims.getSubject()),
					admin,
					profileId == null ? null : UUID.fromString(profileId)));
		}
		catch (ParseException | JOSEException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
