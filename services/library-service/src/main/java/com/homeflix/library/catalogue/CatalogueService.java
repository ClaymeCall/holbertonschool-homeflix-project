package com.homeflix.library.catalogue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeflix.library.web.NotFoundException;
import com.homeflix.library.web.RejectedRequestException;

/** The catalogue: search, filter, categories and details (CAT-01 to CAT-04, CAT-10). */
@Service
public class CatalogueService {

	static final int MAX_TEXT_LENGTH = 100;
	static final int MAX_GENRE_LENGTH = 50;
	static final int DEFAULT_LIMIT = 50;
	static final int MAX_LIMIT = 100;

	private final CatalogueRepository catalogue;

	CatalogueService(CatalogueRepository catalogue) {
		this.catalogue = catalogue;
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

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
