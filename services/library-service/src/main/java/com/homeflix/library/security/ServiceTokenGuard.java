package com.homeflix.library.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.homeflix.library.web.UnauthenticatedException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Authenticates the calls other services make on {@code /internal} paths (ADR-0022). Each caller has its own
 * secret and may use only its own endpoints, so a leaked secret opens nothing else. The secret travels in
 * {@code X-Service-Token}. A user token is never accepted here, and nothing the caller sends is trusted for
 * anything but the endpoint it is allowed to call.
 */
@Component
public class ServiceTokenGuard {

	static final String HEADER = "X-Service-Token";

	private final byte[] acquisitionToken;
	private final byte[] identityToken;

	ServiceTokenGuard(@Value("${homeflix.internal.acquisition-token:}") String acquisitionToken,
			@Value("${homeflix.internal.identity-token:}") String identityToken) {
		if (acquisitionToken == null || acquisitionToken.isBlank()) {
			throw new IllegalStateException(
					"Set ACQUISITION_TO_LIBRARY_TOKEN (homeflix.internal.acquisition-token): the secret the acquisition service sends to the library.");
		}
		if (identityToken == null || identityToken.isBlank()) {
			throw new IllegalStateException(
					"Set IDENTITY_TO_LIBRARY_TOKEN (homeflix.internal.identity-token): the secret the identity service sends to the library.");
		}
		this.acquisitionToken = acquisitionToken.getBytes(StandardCharsets.UTF_8);
		this.identityToken = identityToken.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Accepts the acquisition service, and nobody else.
	 *
	 * @throws UnauthenticatedException 401 when the secret is missing or wrong
	 */
	public void requireAcquisition(HttpServletRequest request) {
		require(request, acquisitionToken);
	}

	/**
	 * Accepts the identity service, and nobody else (not the acquisition service, not a user).
	 *
	 * @throws UnauthenticatedException 401 when the secret is missing or wrong
	 */
	public void requireIdentity(HttpServletRequest request) {
		require(request, identityToken);
	}

	private static void require(HttpServletRequest request, byte[] expected) {
		String given = request.getHeader(HEADER);
		// Constant time, so the answer does not tell how much of a guess was right.
		if (given == null || !MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
			throw new UnauthenticatedException();
		}
	}

}
