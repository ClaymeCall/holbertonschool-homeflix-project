package com.homeflix.library.catalogue;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /media/{mediaId}}: every field an administrator can edit (CAT-09). It replaces them
 * all, so a field left out or null is cleared, except the title which is required. The genre names and the
 * poster address need more than a format check and are validated in {@link CatalogueService}.
 */
public record MediaUpdateRequest(
		@NotBlank(message = "Enter a title.")
		@Size(max = CatalogueService.MAX_TITLE_LENGTH, message = "The title is too long.")
		String title,
		@Size(max = CatalogueService.MAX_DESCRIPTION_LENGTH, message = "The description is too long.")
		String description,
		List<String> genres,
		@PositiveOrZero(message = "The duration cannot be negative.")
		Integer durationSeconds,
		String posterUrl) {

}
