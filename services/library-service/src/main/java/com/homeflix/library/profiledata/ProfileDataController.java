package com.homeflix.library.profiledata;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.library.security.ServiceTokenGuard;
import com.homeflix.library.web.NotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The call of the identity service when a profile is deleted (docs/api/library.yaml, tag Internal; consistency
 * rules, "Deleting a profile"). Only that service may make it (ADR-0022). The gateway answers 404 for this
 * path, so a browser never reaches it.
 */
@RestController
@RequestMapping("/internal/profiles/{profileId}/data")
class ProfileDataController {

	private final ProfileDataService service;
	private final ServiceTokenGuard services;

	ProfileDataController(ProfileDataService service, ServiceTokenGuard services) {
		this.service = service;
		this.services = services;
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(HttpServletRequest request, @PathVariable String profileId) {
		// The secret is checked before the id is read, so a caller without it learns nothing from the id's shape.
		services.requireIdentity(request);
		service.deleteAllOf(parse(profileId));
	}

	private static UUID parse(String profileId) {
		try {
			return UUID.fromString(profileId);
		}
		catch (IllegalArgumentException e) {
			throw new NotFoundException();
		}
	}

}
