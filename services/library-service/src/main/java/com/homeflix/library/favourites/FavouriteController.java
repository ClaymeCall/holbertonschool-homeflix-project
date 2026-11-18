package com.homeflix.library.favourites;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.library.catalogue.CatalogueService;
import com.homeflix.library.catalogue.MediaSummary;
import com.homeflix.library.security.AccessGuard;
import com.homeflix.library.web.NotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The list of a profile, "My List" (docs/api/library.yaml, tag Favourites). There is no profile in the
 * path: the profile is the one in the token (PRF-04).
 */
@RestController
class FavouriteController {

	private final FavouriteRepository favourites;
	private final CatalogueService catalogue;
	private final AccessGuard access;

	FavouriteController(FavouriteRepository favourites, CatalogueService catalogue, AccessGuard access) {
		this.favourites = favourites;
		this.catalogue = catalogue;
		this.access = access;
	}

	@GetMapping("/favourites")
	@Transactional(readOnly = true)
	List<MediaSummary> list(HttpServletRequest request) {
		return catalogue.summaries(favourites.ids(access.requireProfile(request)));
	}

	@PutMapping("/favourites/{mediaId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void add(HttpServletRequest request, @PathVariable UUID mediaId) {
		UUID profileId = access.requireProfile(request);
		if (!favourites.titleExists(mediaId)) {
			throw new NotFoundException();
		}
		favourites.add(profileId, mediaId);
	}

	@DeleteMapping("/favourites/{mediaId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void remove(HttpServletRequest request, @PathVariable UUID mediaId) {
		favourites.remove(access.requireProfile(request), mediaId);
	}

}
