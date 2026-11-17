package com.homeflix.library.web;

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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.homeflix.library.storage.MediaAccessDeniedException;

/**
 * Turns every exception into the common error body, with the status and message of
 * docs/architecture/error-model.md. Technical details go to the log only (SEC-04).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	static final String SERVICE_UNAVAILABLE_MESSAGE =
			"This part of HomeFlix is temporarily unavailable. Please try again in a moment.";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return ResponseEntity.badRequest().body(new ApiError("validation_failed", "Some fields are invalid.", fields));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest()
				.body(new ApiError("invalid_request", "The request body is missing or is not valid JSON."));
	}

	/** A media file is missing, or its real path is outside the authorized roots (PLY-04, PLY-05). */
	@ExceptionHandler(MediaAccessDeniedException.class)
	ResponseEntity<ApiError> mediaAccess(MediaAccessDeniedException ex) {
		return switch (ex.reason()) {
			case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("file_not_found",
					"The file for this title could not be found. It may have been moved or deleted from the library."));
			case OUTSIDE_ROOTS -> ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new ApiError("access_denied", "You do not have access to this video."));
		};
	}

	/** A called service is down, too slow, or answered with a 5xx: one answer for all of them. */
	@ExceptionHandler({ ServiceUnavailableException.class, ResourceAccessException.class,
			HttpServerErrorException.class })
	ResponseEntity<ApiError> serviceUnavailable(Exception ex) {
		log.warn("A service this one depends on is unavailable", ex);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ApiError("service_unavailable", SERVICE_UNAVAILABLE_MESSAGE));
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

}
