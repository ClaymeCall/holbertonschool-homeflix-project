package com.homeflix.acquisition.downloads;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A download, as returned by the API ({@code Download} in docs/api/acquisition.yaml). The account that
 * started it and the torrent's info hash stay in the service.
 */
public record Download(
		UUID id,
		UUID entryId,
		DownloadState state,
		int progressPercent,
		String errorCode,
		OffsetDateTime createdAt,
		OffsetDateTime finishedAt) {

}
