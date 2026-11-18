package com.homeflix.library.indexing;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The result of one scan, as in {@code ScanReport} of docs/api/library.yaml. It is not stored. */
public record ScanReport(
		OffsetDateTime startedAt,
		OffsetDateTime finishedAt,
		int added,
		int updated,
		int markedUnavailable,
		List<Unsupported> unsupported,
		List<InaccessibleRoot> inaccessibleRoots) {

	/** A video in a format HomeFlix does not play: reported, not added. */
	public record Unsupported(UUID rootId, String relativePath, String error) {
	}

	/** A root that could not be read during this scan. {@code error} is one of three stable codes. */
	public record InaccessibleRoot(UUID rootId, String error) {
	}

}
