package com.homeflix.acquisition.catalogue;

import java.util.UUID;

/**
 * An entry of the verified acquisition catalogue, as returned by the API ({@code Entry} in
 * docs/api/acquisition.yaml). The download address is left out on purpose: the service keeps it and a
 * client never sees nor sends one (ACQ-09, R-15).
 */
public record Entry(
		UUID id,
		String title,
		String sourceName,
		String sourceUrl,
		String licence,
		String licenceUrl,
		String attribution,
		long expectedSizeBytes,
		String expectedFormat) {

}
