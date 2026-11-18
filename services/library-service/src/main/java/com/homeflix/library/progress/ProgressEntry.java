package com.homeflix.library.progress;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.homeflix.library.catalogue.MediaSummary;

/**
 * Where a profile stopped in a title (docs/api/library.yaml). {@code state} is null when 5 seconds or less
 * were watched. {@code media} is filled by the list only.
 */
public record ProgressEntry(
		UUID mediaId,
		int positionSeconds,
		int durationSeconds,
		ProgressState state,
		Instant updatedAt,
		@JsonInclude(JsonInclude.Include.NON_NULL) MediaSummary media) {

	ProgressEntry withMedia(MediaSummary summary) {
		return new ProgressEntry(mediaId, positionSeconds, durationSeconds, state, updatedAt, summary);
	}

}
