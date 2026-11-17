package com.homeflix.identity.web;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error body returned by the identity service. Same shape as the gateway errors
 * ({"error": ..., "message": ...}), with field messages for validation errors.
 * Provisional until the common error format is defined (issue #16).
 * Never contains technical details (SEC-04).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String error, String message, Map<String, String> fields) {

	public ApiError(String error, String message) {
		this(error, message, Map.of());
	}

}