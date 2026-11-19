package com.homeflix.acquisition.downloads;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.acquisition.security.AdminOnly;
import com.homeflix.acquisition.security.Caller;

import jakarta.validation.Valid;

/**
 * Starting and cancelling downloads (docs/api/acquisition.yaml, tag Downloads; ACQ-03). Administrator only
 * (ACQ-05): {@link AdminOnly} checks the caller before Spring reads the path or the body, so a caller who is
 * not an administrator gets 401 or 403 whatever the request holds.
 */
@AdminOnly
@RestController
@RequestMapping("/downloads")
class DownloadController {

	private final DownloadService downloads;

	DownloadController(DownloadService downloads) {
		this.downloads = downloads;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Download start(@RequestAttribute(AdminOnly.CALLER) Caller caller, @Valid @RequestBody StartRequest body) {
		return downloads.start(body.entryId(), caller.accountId());
	}

	@PostMapping("/{downloadId}/cancel")
	Download cancel(@PathVariable UUID downloadId) {
		return downloads.cancel(downloadId);
	}

	@GetMapping("/{downloadId}")
	Download get(@PathVariable UUID downloadId) {
		return downloads.get(downloadId);
	}

}
