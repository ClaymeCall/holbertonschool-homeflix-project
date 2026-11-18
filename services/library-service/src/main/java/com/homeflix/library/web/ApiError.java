package com.homeflix.library.web;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The common error body of every HomeFlix service and of the gateway (ADR-0020,
 * docs/architecture/error-model.md): {@code {"error": "...", "message": "...", "fields": {...}}}.
 * {@code fields} only appears for validation errors. Never contains technical details (SEC-04).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String error, String message, Map<String, String> fields) {

	public ApiError(String error, String message) {
		this(error, message, Map.of());
	}

}
