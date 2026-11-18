package com.homeflix.acquisition.security;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Finds the caller of a request. The implementation reads and verifies the session token with
 * the identity service's public key ({@link TokenCallerProvider}, issue G5). Same contract as in
 * the library service.
 */
public interface CallerProvider {

	/** The caller, or empty when the request has no valid token. */
	Optional<Caller> current(HttpServletRequest request);

}
