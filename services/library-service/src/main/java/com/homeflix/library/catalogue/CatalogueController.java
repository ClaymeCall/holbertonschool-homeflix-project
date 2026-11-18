package com.homeflix.library.catalogue;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.library.security.AccessGuard;

import jakarta.servlet.http.HttpServletRequest;

/** The catalogue (docs/api/library.yaml, tag Catalogue). Open to any signed-in account. */
@RestController
class CatalogueController {

	private final CatalogueService catalogue;
	private final AccessGuard access;

	CatalogueController(CatalogueService catalogue, AccessGuard access) {
		this.catalogue = catalogue;
		this.access = access;
	}

	@GetMapping("/media")
	List<MediaSummary> list(HttpServletRequest request,
			@RequestParam(name = "query", required = false) String query,
			@RequestParam(name = "genre", required = false) String genre,
			@RequestParam(name = "sort", required = false) String sort,
			@RequestParam(name = "limit", required = false) Integer limit,
			@RequestParam(name = "offset", required = false) Integer offset) {
		access.requireAccount(request);
		return catalogue.search(query, genre, sort, limit, offset);
	}

	@GetMapping("/media/{mediaId}")
	MediaDetails details(HttpServletRequest request, @PathVariable UUID mediaId) {
		access.requireAccount(request);
		return catalogue.details(mediaId);
	}

	@GetMapping("/categories")
	List<Category> categories(HttpServletRequest request) {
		access.requireAccount(request);
		return catalogue.categories();
	}

	@GetMapping("/genres")
	List<String> genres(HttpServletRequest request) {
		access.requireAccount(request);
		return catalogue.genres();
	}

}
