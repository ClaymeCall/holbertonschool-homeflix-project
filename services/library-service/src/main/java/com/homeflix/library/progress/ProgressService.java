package com.homeflix.library.progress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeflix.library.catalogue.CatalogueService;
import com.homeflix.library.catalogue.MediaSummary;
import com.homeflix.library.web.NotFoundException;
import com.homeflix.library.web.RejectedRequestException;

/** Viewing progress of the active profile (PLY-07 to PLY-09). The profile always comes from the token. */
@Service
class ProgressService {

	static final int DEFAULT_LIMIT = 50;
	static final int MAX_LIMIT = 100;

	private final ProgressRepository progress;
	private final CatalogueService catalogue;

	ProgressService(ProgressRepository progress, CatalogueService catalogue) {
		this.progress = progress;
		this.catalogue = catalogue;
	}

	/** @throws NotFoundException when there is no such title */
	@Transactional
	ProgressEntry save(UUID profileId, UUID mediaId, ProgressRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		Integer position = request.positionSeconds();
		Integer duration = request.durationSeconds();
		if (position == null || position < 0) {
			errors.put("positionSeconds", "The position cannot be negative.");
		}
		if (duration == null || duration < 1) {
			errors.put("durationSeconds", "The duration must be at least 1 second.");
		}
		if (errors.isEmpty() && position > duration) {
			errors.put("positionSeconds", "The position cannot be after the end.");
		}
		if (!errors.isEmpty()) {
			throw new RejectedRequestException(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.",
					errors);
		}
		if (!progress.titleExists(mediaId)) {
			throw new NotFoundException();
		}
		return progress.save(profileId, mediaId, position, duration);
	}

	/** @throws NotFoundException when nothing was saved, the normal case for a title not seen yet */
	@Transactional(readOnly = true)
	ProgressEntry get(UUID profileId, UUID mediaId) {
		return progress.find(profileId, mediaId).orElseThrow(NotFoundException::new);
	}

	@Transactional
	void reset(UUID profileId, UUID mediaId) {
		progress.delete(profileId, mediaId);
	}

	@Transactional(readOnly = true)
	List<ProgressEntry> list(UUID profileId, String state, Integer limit, Integer offset) {
		Map<String, String> errors = new LinkedHashMap<>();
		Optional<ProgressState> wanted = Optional.empty();
		if (state != null && !state.isBlank()) {
			try {
				wanted = Optional.of(ProgressState.valueOf(state));
			}
			catch (IllegalArgumentException e) {
				errors.put("state", "Use STARTED or COMPLETED.");
			}
		}
		int pageSize = limit == null ? DEFAULT_LIMIT : limit;
		if (pageSize < 1 || pageSize > MAX_LIMIT) {
			errors.put("limit", "Ask for between 1 and " + MAX_LIMIT + " entries.");
		}
		int skipped = offset == null ? 0 : offset;
		if (skipped < 0) {
			errors.put("offset", "The offset cannot be negative.");
		}
		if (!errors.isEmpty()) {
			throw new RejectedRequestException(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.",
					errors);
		}
		List<ProgressEntry> entries = progress.list(profileId, wanted.orElse(null), pageSize, skipped);
		Map<UUID, MediaSummary> titles = new LinkedHashMap<>();
		catalogue.summaries(entries.stream().map(ProgressEntry::mediaId).toList())
				.forEach(summary -> titles.put(summary.id(), summary));
		return entries.stream().filter(entry -> titles.containsKey(entry.mediaId()))
				.map(entry -> entry.withMedia(titles.get(entry.mediaId()))).toList();
	}

}
