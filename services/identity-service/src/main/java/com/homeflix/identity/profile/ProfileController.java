package com.homeflix.identity.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.identity.security.AccessGuard;
import com.homeflix.identity.security.Caller;
import com.homeflix.identity.session.AccountView;
import com.homeflix.identity.session.SessionCookies;
import com.homeflix.identity.session.SessionService;
import com.homeflix.identity.session.SignedIn;
import com.homeflix.identity.web.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Profile endpoints of docs/api/identity.yaml, all {@code x-permission: account} (PRF-01 to PRF-04).
 * Public paths through the gateway: /api/identity/profiles/... (ADR-0017). The account always comes
 * from the token, never from the request (R-06).
 */
@RestController
@RequestMapping("/profiles")
public class ProfileController {

	private final AccessGuard guard;
	private final ProfileService profiles;
	private final SessionService sessions;
	private final SessionCookies cookies;

	public ProfileController(AccessGuard guard, ProfileService profiles, SessionService sessions,
			SessionCookies cookies) {
		this.guard = guard;
		this.profiles = profiles;
		this.sessions = sessions;
		this.cookies = cookies;
	}

	@GetMapping
	public List<Profile> list(HttpServletRequest request) {
		return profiles.list(guard.requireAccount(request));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Profile create(HttpServletRequest request, @Valid @RequestBody ProfileRequest body) {
		return profiles.create(guard.requireAccount(request), body);
	}

	@PutMapping("/{profileId}")
	public Profile rename(HttpServletRequest request, @PathVariable("profileId") UUID profileId,
			@Valid @RequestBody ProfileRequest body) {
		return profiles.rename(guard.requireAccount(request), profileId, body);
	}

	@DeleteMapping("/{profileId}")
	public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable("profileId") UUID profileId) {
		profiles.delete(guard.requireAccount(request), profileId);
		return ResponseEntity.noContent().build();
	}

	/** Re-issues the session cookie with the {@code profileId} claim (identity design, PRF-02). */
	@PostMapping("/{profileId}/select")
	public ResponseEntity<AccountView> select(HttpServletRequest request, @PathVariable("profileId") UUID profileId) {
		Caller caller = guard.requireAccount(request);
		SignedIn signedIn = sessions.selectProfile(caller.accountId(), profileId);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookies.session(signedIn.token()).toString())
				.body(signedIn.account());
	}

	@ExceptionHandler(ProfileLimitReachedException.class)
	ResponseEntity<ApiError> limitReached(ProfileLimitReachedException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("profile_limit_reached", ex.getMessage()));
	}

	@ExceptionHandler(ProfileNameTakenException.class)
	ResponseEntity<ApiError> nameTaken(ProfileNameTakenException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("profile_name_taken", ex.getMessage()));
	}

}
