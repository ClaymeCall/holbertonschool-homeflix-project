package com.homeflix.identity.setup;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.identity.web.ApiError;

import jakarta.validation.Valid;

/**
 * First-run setup endpoints. Public path through the gateway: /api/identity/setup/...
 * (ADR-0017). Both are anonymous by design: no account exists yet.
 */
@RestController
@RequestMapping("/setup")
public class SetupController {

	private final SetupService setupService;

	public SetupController(SetupService setupService) {
		this.setupService = setupService;
	}

	@GetMapping("/status")
	public SetupStatus status() {
		return setupService.status();
	}

	@PostMapping("/admin")
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedAdministrator createFirstAdministrator(@Valid @RequestBody SetupRequest request) {
		return setupService.createFirstAdministrator(request);
	}

	@ExceptionHandler(SetupAlreadyCompletedException.class)
	ResponseEntity<ApiError> setupAlreadyCompleted() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("setup_already_completed", "HomeFlix is already set up. Sign in instead."));
	}

}
