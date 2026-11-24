package com.homeflix.acquisition.downloads;

/**
 * The plain-language reason shown for a failed download (ACQ-03, ADR-0021: the interface shows the server's
 * message as written). It never holds a path, a host or another technical detail, which go to the log only
 * (SEC-04).
 */
final class FailureMessages {

	static final String TORRENT_MISSING = "torrent_missing";
	static final String VERIFICATION_FAILED = "verification_failed";
	static final String HANDOFF_FAILED = "handoff_failed";
	static final String FILE_UNREADABLE = "file_unreadable";

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
			case VERIFICATION_FAILED ->
				"The downloaded file is not the expected video, so it was deleted. You can start it again.";
			case HANDOFF_FAILED ->
				"The video was downloaded but could not be added to the library. You can start it again.";
			case FILE_UNREADABLE ->
				"HomeFlix cannot read the downloaded file, so it was kept. Ask an administrator to check the "
						+ "permissions of the downloads folder, then start it again.";
			default -> "This download failed. You can start it again.";
		};
	}

}
