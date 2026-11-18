package com.homeflix.library.catalogue;

import java.util.List;
import java.util.UUID;

/**
 * A title as shown in lists (docs/api/library.yaml). Only the id, the title and the availability are
 * always filled: a missing description, poster or duration is {@code null}, never an empty string (CAT-10).
 */
public record MediaSummary(
		UUID id,
		String title,
		List<String> genres,
		String posterUrl,
		Integer durationSeconds,
		String availability) {

}
