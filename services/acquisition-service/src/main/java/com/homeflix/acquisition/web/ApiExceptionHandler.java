package com.homeflix.acquisition.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Turns every exception into the common error body, with the status and message of
 * docs/architecture/error-model.md. Technical details go to the log only (SEC-04).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private static final ApiError NOT_FOUND = new ApiError("not_found", "This address does not exist.");

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

	@ExceptionHandler(UnauthenticatedException.class)
	ResponseEntity<ApiError> unauthenticated(UnauthenticatedException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("unauthenticated", ex.getMessage()));
	}

	@ExceptionHandler(ForbiddenException.class)
	ResponseEntity<ApiError> forbidden(ForbiddenException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("forbidden", ex.getMessage()));
	}

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiError> notFound(NotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(NOT_FOUND);
	}

	/**
	 * A value that cannot be read as the expected type. An id in the address cannot match anything, so it is
	 * a plain 404, as in the library. A value in the query string is a validation error that names it.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiError> badValue(MethodArgumentTypeMismatchException ex) {
		if (ex.getParameter().hasParameterAnnotation(PathVariable.class)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(NOT_FOUND);
		}
		return ResponseEntity.badRequest().body(new ApiError("validation_failed", "Some fields are invalid.",
				Map.of(ex.getName(), "This value is not valid.")));
	}

	/** A refusal with its own code and a message the user can act on. */
	@ExceptionHandler(RejectedRequestException.class)
	ResponseEntity<ApiError> rejected(RejectedRequestException ex) {
		return ResponseEntity.status(ex.status()).body(new ApiError(ex.code(), ex.getMessage(), ex.fields()));
	}

	/**
	 * A called service or this service's own database is down, too slow, or answered with a 5xx: one answer
	 * for all of them (error model, "Database down").
	 */
	@ExceptionHandler({ ServiceUnavailableException.class, ResourceAccessException.class,
			HttpServerErrorException.class,
			DataAccessResourceFailureException.class, QueryTimeoutException.class, CannotCreateTransactionException.class })
	ResponseEntity<ApiError> serviceUnavailable(Exception ex) {
		if (ex instanceof ServiceUnavailableException) {
			// Thrown by a client of this service, which has already logged the cause with its details.
			log.warn("A service or database this one depends on is unavailable: {}", ex.getMessage());
		}
		else {
			log.warn("A service or database this one depends on is unavailable", ex);
		}
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ApiError("service_unavailable", SERVICE_UNAVAILABLE_MESSAGE));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> unexpected(Exception ex) {
		// Framework errors (wrong method, unsupported media type...) keep their status.
		if (ex instanceof ErrorResponse errorResponse) {
			HttpStatusCode status = errorResponse.getStatusCode();
			ApiError body = status.value() == HttpStatus.NOT_FOUND.value()
					? NOT_FOUND
					: new ApiError("request_rejected", "This request cannot be processed.");
			return ResponseEntity.status(status).body(body);
		}
		log.error("Unexpected error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiError("internal_error", "Something went wrong. Please try again."));
	}

}
