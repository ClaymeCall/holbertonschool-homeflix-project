package com.homeflix.library.indexing;

/**
 * Outcome of the identification rules for one file found during indexing.
 * See docs/library/media-identification.md.
 */
public enum MediaFileKind {

	/** A video HomeFlix can play: it is indexed. */
	SUPPORTED_VIDEO(null),

	/** A video in a format HomeFlix does not play: it is reported, not indexed. */
	UNSUPPORTED_VIDEO("format_not_supported"),

	/** Not a video (subtitle, image, hidden or partial file): silently skipped. */
	IGNORED(null);

	private final String errorCode;

	MediaFileKind(String errorCode) {
		this.errorCode = errorCode;
	}

	/** Machine-readable outcome code reported to the administrator, or null when nothing is reported. */
	public String errorCode() {
		return errorCode;
	}

}
