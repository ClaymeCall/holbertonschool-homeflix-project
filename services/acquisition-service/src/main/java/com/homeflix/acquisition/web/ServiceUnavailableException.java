package com.homeflix.acquisition.web;

/**
 * A service this one depends on is down, too slow or answered with an error (R-24).
 * Answered to the caller as 503 {@code service_unavailable}. The cause is logged, never returned.
 */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
