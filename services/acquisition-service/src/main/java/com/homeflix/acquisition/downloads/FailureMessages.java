package com.homeflix.acquisition.downloads;

/**
 * The plain-language reason shown for a failed download (ACQ-03, ADR-0021: the interface shows the server's
 * message as written). It never holds a path, a host or another technical detail, which go to the log only
 * (SEC-04).
 */
final class FailureMessages {

	static final String TORRENT_MISSING = "torrent_missing";

	private FailureMessages() {
	}

	/** The message for an error code, or null when there is no error. */
	static String forCode(String errorCode) {
		if (errorCode == null) {
			return null;
		}
		return switch (errorCode) {
			case DownloadService.CANCELLED -> "This download was cancelled.";
			case DownloadService.CLIENT_ERROR ->
				"The download service could not download this title. You can start it again.";
			case TORRENT_MISSING -> "This download is no longer in the download service. You can start it again.";
			default -> "This download failed. You can start it again.";
		};
	}

}
