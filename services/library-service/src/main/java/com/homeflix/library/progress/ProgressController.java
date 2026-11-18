package com.homeflix.library.progress;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.library.security.AccessGuard;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Viewing progress (docs/api/library.yaml, tag Progress). There is no profile in the path: the profile is
 * the one in the token (PRF-04).
 */
@RestController
class ProgressController {

	private final ProgressService progress;
	private final AccessGuard access;

	ProgressController(ProgressService progress, AccessGuard access) {
		this.progress = progress;
		this.access = access;
	}

	@GetMapping("/progress")
	List<ProgressEntry> list(HttpServletRequest request,
			@RequestParam(name = "state", required = false) String state,
			@RequestParam(name = "limit", required = false) Integer limit,
			@RequestParam(name = "offset", required = false) Integer offset) {
		return progress.list(access.requireProfile(request), state, limit, offset);
	}

	@GetMapping("/progress/{mediaId}")
	ProgressEntry get(HttpServletRequest request, @PathVariable UUID mediaId) {
		return progress.get(access.requireProfile(request), mediaId);
	}

	@PutMapping("/progress/{mediaId}")
	ProgressEntry save(HttpServletRequest request, @PathVariable UUID mediaId, @RequestBody ProgressRequest body) {
		return progress.save(access.requireProfile(request), mediaId, body);
	}

	@DeleteMapping("/progress/{mediaId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reset(HttpServletRequest request, @PathVariable UUID mediaId) {
		progress.reset(access.requireProfile(request), mediaId);
	}

}
