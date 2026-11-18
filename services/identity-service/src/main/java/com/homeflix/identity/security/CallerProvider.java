package com.homeflix.identity.security;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Finds the caller of a request from its session token. Same contract as in the library and
 * acquisition services.
 */
public interface CallerProvider {

	/** The caller, or empty when the request has no valid token. */
	Optional<Caller> current(HttpServletRequest request);

}
