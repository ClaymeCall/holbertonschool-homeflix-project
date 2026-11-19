package com.homeflix.identity.web;

/**
 * A service this one depends on did not answer, or answered with an error: answered 503
 * {@code service_unavailable} (ADR-0020). Nothing was changed, so the user can try again.
 */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String reason, Throwable cause) {
		super(reason, cause);
	}

}
