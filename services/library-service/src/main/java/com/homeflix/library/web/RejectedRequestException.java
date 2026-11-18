package com.homeflix.library.web;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * A request refused for a reason the user can act on, with its own stable error code
 * (docs/architecture/error-model.md). The message is written for the user and never holds a path
 * or other technical detail (SEC-04).
 */
public class RejectedRequestException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final Map<String, String> fields;

	public RejectedRequestException(HttpStatus status, String code, String message) {
		this(status, code, message, Map.of());
	}

	public RejectedRequestException(HttpStatus status, String code, String message, Map<String, String> fields) {
		super(message);
		this.status = status;
		this.code = code;
		this.fields = fields;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public Map<String, String> fields() {
		return fields;
	}

}
