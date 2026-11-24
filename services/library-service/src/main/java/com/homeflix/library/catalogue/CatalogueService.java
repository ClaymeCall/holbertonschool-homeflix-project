package com.homeflix.library.catalogue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeflix.library.web.NotFoundException;
import com.homeflix.library.web.RejectedRequestException;

/** The catalogue: search, filter, categories and details (CAT-01 to CAT-04, CAT-10). */
@Service
public class CatalogueService {

	static final int MAX_TITLE_LENGTH = 500;
	static final int MAX_DESCRIPTION_LENGTH = 5000;
	static final int MAX_GENRES = 10;
	static final int MAX_TEXT_LENGTH = 100;
	static final int MAX_GENRE_LENGTH = 50;
	static final int DEFAULT_LIMIT = 50;
	static final int MAX_LIMIT = 100;

	private final CatalogueRepository catalogue;
	private final PosterPolicy posters;

	CatalogueService(CatalogueRepository catalogue, PosterPolicy posters) {
		this.catalogue = catalogue;
		this.posters = posters;
	}

	/**
	 * Lists titles whatever their availability, so unavailable and downloading titles can be shown as
	 * such. Every parameter is checked here and a bad one is a 400 with the field named (SEC-01).
	 */
	@Transactional(readOnly = true)
	public List<MediaSummary> search(String text, String genre, String sort, Integer limit, Integer offset) {
		Map<String, String> errors = new LinkedHashMap<>();

		String trimmedText = blankToNull(text);
		if (trimmedText != null && trimmedText.length() > MAX_TEXT_LENGTH) {
			errors.put("query", "Search for at most " + MAX_TEXT_LENGTH + " characters.");
		}
		String trimmedGenre = blankToNull(genre);
		if (trimmedGenre != null && trimmedGenre.length() > MAX_GENRE_LENGTH) {
			errors.put("genre", "A genre has at most " + MAX_GENRE_LENGTH + " characters.");
		}
		CatalogueQuery.Sort order = CatalogueQuery.Sort.TITLE;
		if (sort != null && !sort.isBlank()) {
			switch (sort) {
				case "recent" -> order = CatalogueQuery.Sort.RECENT;
				case "title" -> order = CatalogueQuery.Sort.TITLE;
				default -> errors.put("sort", "Sort by recent or title.");
			}
		}
		int pageSize = limit == null ? DEFAULT_LIMIT : limit;
		if (pageSize < 1 || pageSize > MAX_LIMIT) {
			errors.put("limit", "Ask for between 1 and " + MAX_LIMIT + " titles.");
		}
		int skipped = offset == null ? 0 : offset;
		if (skipped < 0) {
			errors.put("offset", "The offset cannot be negative.");
		}
		if (!errors.isEmpty()) {
			throw new RejectedRequestException(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.",
					errors);
		}
		return catalogue.search(new CatalogueQuery(trimmedText, trimmedGenre, order, pageSize, skipped));
	}

	/** The summaries of these titles, in the order given. Used by the lists of a profile (favourites, progress). */
	@Transactional(readOnly = true)
	public List<MediaSummary> summaries(List<UUID> ids) {
		return catalogue.summariesOf(ids);
	}

	/** @throws NotFoundException when there is no such title */
	@Transactional(readOnly = true)
	public MediaDetails details(UUID id) {
		return catalogue.find(id).orElseThrow(NotFoundException::new);
	}

	@Transactional(readOnly = true)
	public List<Category> categories() {
		return catalogue.categories();
	}

	@Transactional(readOnly = true)
	public List<String> genres() {
		return catalogue.genres();
	}

	/**
	 * Replaces the editable fields of a title (CAT-09, CAT-10). Availability and the file are not touched:
	 * they belong to the indexer. A scan never writes these fields, so the edit survives a re-index.
	 *
	 * @throws RejectedRequestException 400 {@code validation_failed} naming each invalid field
	 * @throws NotFoundException when there is no such title
	 */
	@Transactional
	public MediaDetails update(UUID id, MediaUpdateRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		String title = request.title().trim();
		String description = blankToNull(request.description());
		Set<String> genres = cleanGenres(request.genres(), errors);
		String poster = cleanPoster(request.posterUrl(), errors);
		if (!errors.isEmpty()) {
			throw new RejectedRequestException(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.",
					errors);
		}
		if (!catalogue.update(id, title, description, request.durationSeconds(), poster)) {
			throw new NotFoundException();
		}
		catalogue.replaceGenres(id, genres);
		return catalogue.find(id).orElseThrow(NotFoundException::new);
	}

	/**
	 * Removes a catalogue entry: only the database row, with its favourites, progress and genre links. The
	 * file on disk and its {@code media_file} row are left alone, so the next scan finds the file already
	 * known and does not create the entry again: a removed entry stays removed
	 * (docs/library/media-identification.md).
	 *
	 * @throws NotFoundException when there is no such title
	 */
	@Transactional
	public void remove(UUID id) {
		if (!catalogue.delete(id)) {
			throw new NotFoundException();
		}
	}

	private static Set<String> cleanGenres(List<String> genres, Map<String, String> errors) {
		Set<String> kept = new LinkedHashSet<>();
		if (genres == null) {
			return kept;
		}
		Set<String> lowered = new LinkedHashSet<>();
		for (String raw : genres) {
			String name = raw == null ? "" : raw.trim();
			if (name.isEmpty() || name.length() > MAX_GENRE_LENGTH) {
				errors.put("genres", "A genre has between 1 and " + MAX_GENRE_LENGTH + " characters.");
				return kept;
			}
			if (lowered.add(name.toLowerCase(Locale.ROOT))) {
				kept.add(name);
			}
		}
		if (kept.size() > MAX_GENRES) {
			errors.put("genres", "A title has at most " + MAX_GENRES + " genres.");
		}
		return kept;
	}

	/** The poster address if the policy accepts it, null when there is none. The server never fetches it (ADR-0018). */
	private String cleanPoster(String value, Map<String, String> errors) {
		String poster = blankToNull(value);
		if (poster == null) {
			return null;
		}
		var refusal = posters.refusal(poster);
		if (refusal.isPresent()) {
			errors.put("posterUrl", refusal.get());
			return null;
		}
		return poster;
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
