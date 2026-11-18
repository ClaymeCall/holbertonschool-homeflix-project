package com.homeflix.acquisition.downloads;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.acquisition.security.AccessGuard;
import com.homeflix.acquisition.security.Caller;
import com.homeflix.acquisition.web.RejectedRequestException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Starting and cancelling downloads (docs/api/acquisition.yaml, tag Downloads; ACQ-03). Administrator
 * only (ACQ-05): the guard is called first in every operation, so a caller who is not an administrator gets
 * 401 or 403 whatever the body holds, and the body is validated only after that.
 */
@RestController
@RequestMapping("/downloads")
class DownloadController {

	private final DownloadService downloads;
	private final AccessGuard access;
	private final Validator validator;

	DownloadController(DownloadService downloads, AccessGuard access, Validator validator) {
		this.downloads = downloads;
		this.access = access;
		this.validator = validator;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Download start(HttpServletRequest request, @RequestBody StartRequest body) {
		Caller caller = access.requireAdmin(request);
		validate(body);
		return downloads.start(body.entryId(), caller.accountId());
	}

	@PostMapping("/{downloadId}/cancel")
	Download cancel(HttpServletRequest request, @PathVariable UUID downloadId) {
		access.requireAdmin(request);
		return downloads.cancel(downloadId);
	}

	@GetMapping("/{downloadId}")
	Download get(HttpServletRequest request, @PathVariable UUID downloadId) {
		access.requireAdmin(request);
		return downloads.get(downloadId);
	}

	/** The same 400 validation_failed body as a bean validation error, with the message of each field. */
	private void validate(StartRequest body) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (ConstraintViolation<StartRequest> violation : validator.validate(body)) {
			fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
		}
		if (!fields.isEmpty()) {
			throw new RejectedRequestException(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.",
					fields);
		}
	}

}
