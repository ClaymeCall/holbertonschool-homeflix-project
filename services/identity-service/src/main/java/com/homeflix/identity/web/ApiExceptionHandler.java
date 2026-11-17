package com.homeflix.identity.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into {@link ApiError} bodies without technical details (SEC-04).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return validationFailed(fields);
	}

	@ExceptionHandler(InvalidInputException.class)
	ResponseEntity<ApiError> invalidInput(InvalidInputException ex) {
		return validationFailed(Map.of(ex.field(), ex.getMessage()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest()
				.body(new ApiError("invalid_request", "The request body is missing or is not valid JSON."));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> unexpected(Exception ex) {
		// Framework errors (wrong method, unsupported media type...) keep their status.
		if (ex instanceof ErrorResponse errorResponse) {
			HttpStatusCode status = errorResponse.getStatusCode();
			ApiError body = status.value() == HttpStatus.NOT_FOUND.value()
					? new ApiError("not_found", "This address does not exist.")
					: new ApiError("request_rejected", "This request cannot be processed.");
			return ResponseEntity.status(status).body(body);
		}
		log.error("Unexpected error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiError("internal_error", "Something went wrong. Please try again."));
	}

	private static ResponseEntity<ApiError> validationFailed(Map<String, String> fields) {
		return ResponseEntity.badRequest()
				.body(new ApiError("validation_failed", "Some fields are invalid.", fields));
	}

}