package com.homeflix.library.downloads;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.library.security.ServiceTokenGuard;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The calls of the acquisition service (docs/api/library.yaml, tag Internal). Only that service may make them
 * (ADR-0022). The gateway answers 404 for these paths, so a browser never reaches them.
 */
@RestController
@RequestMapping("/internal/downloads/{entryId}")
class DownloadController {

	record StartingRequest(@NotBlank @Size(max = 500) String title) {
	}

	record CompletedRequest(@NotBlank @Size(max = 4096) String relativePath) {
	}

	private final DownloadService service;
	private final ServiceTokenGuard services;

	DownloadController(DownloadService service, ServiceTokenGuard services) {
		this.service = service;
		this.services = services;
	}

	@PutMapping
	void starting(HttpServletRequest request, @PathVariable UUID entryId, @Valid @RequestBody StartingRequest body) {
		services.requireAcquisition(request);
		service.starting(entryId, body.title());
	}

	@PostMapping("/completed")
	Map<String, UUID> completed(HttpServletRequest request, @PathVariable UUID entryId,
			@Valid @RequestBody CompletedRequest body) {
		services.requireAcquisition(request);
		return Map.of("mediaId", service.completed(entryId, body.relativePath()));
	}

	@PostMapping("/failed")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void failed(HttpServletRequest request, @PathVariable UUID entryId) {
		services.requireAcquisition(request);
		service.failed(entryId);
	}

}
