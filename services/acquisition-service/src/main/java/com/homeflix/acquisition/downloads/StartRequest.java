package com.homeflix.acquisition.downloads;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /downloads}. It names a catalogue entry only: there is no field for a URL, a magnet
 * link or a file (ACQ-09, ACQ-10), and any other field is ignored. {@code rightsAcknowledged} records that
 * the administrator read the rights reminder (ACQ-11): the server refuses the start without it.
 */
public record StartRequest(
		@NotNull(message = "Choose a title from the catalogue.")
		UUID entryId,

		@NotNull(message = "Confirm that you have read the reminder about rights before downloading.")
		@AssertTrue(message = "Confirm that you have read the reminder about rights before downloading.")
		Boolean rightsAcknowledged) {

}
