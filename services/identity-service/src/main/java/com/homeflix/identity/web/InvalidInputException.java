package com.homeflix.identity.web;

/**
 * Input rejected by a server-side rule that Bean Validation cannot express (SEC-01).
 * Answered with 400 and the field message.
 */
public class InvalidInputException extends RuntimeException {

	private final String field;

	public InvalidInputException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}

}
