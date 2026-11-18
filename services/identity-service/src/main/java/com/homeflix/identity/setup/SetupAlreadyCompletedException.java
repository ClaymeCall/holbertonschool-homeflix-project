package com.homeflix.identity.setup;

/**
 * An administrator already exists, so the first-run setup is closed (ACC-02, R-07).
 */
public class SetupAlreadyCompletedException extends RuntimeException {

	public SetupAlreadyCompletedException() {
		super("An administrator already exists.");
	}

}
