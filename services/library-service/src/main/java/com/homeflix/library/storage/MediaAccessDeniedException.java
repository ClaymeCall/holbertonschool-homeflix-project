package com.homeflix.library.storage;

/**
 * A file could not be opened because it is missing, unreadable or outside the authorized roots.
 *
 * <p>The message is deliberately generic and safe to show to a user (SEC-04): it never
 * contains a path. The precise cause is in {@link #reason()} and in the server log.
 */
public class MediaAccessDeniedException extends RuntimeException {

	/** Why access was refused. Callers map this to user feedback (for example 404 or 403). */
	public enum Reason {
		/** The path does not exist or cannot be resolved. */
		NOT_FOUND,
		/** The file exists but HomeFlix is not allowed to read it, or the disk failed while reading it. */
		UNREADABLE,
		/** The real path is not inside any authorized root. */
		OUTSIDE_ROOTS
	}

	private final Reason reason;

	public MediaAccessDeniedException(Reason reason, Throwable cause) {
		super("This file cannot be accessed.", cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
