package com.homeflix.library.security;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Finds the caller of a request. The real implementation reads and verifies the session token
 * with the identity service's public key (issue G5, #43). Until it exists, the default
 * implementation knows nobody, so every protected endpoint refuses every request.
 */
public interface CallerProvider {

	/** The caller, or empty when the request has no valid token. */
	Optional<Caller> current(HttpServletRequest request);

}
