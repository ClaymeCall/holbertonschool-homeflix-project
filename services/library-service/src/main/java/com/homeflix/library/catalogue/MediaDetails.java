package com.homeflix.library.catalogue;

import java.util.List;
import java.util.UUID;

/** A title with its description, for the details screen (CAT-03). */
public record MediaDetails(
		UUID id,
		String title,
		List<String> genres,
		String posterUrl,
		Integer durationSeconds,
		String availability,
		String description) {

}
