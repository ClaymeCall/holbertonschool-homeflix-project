package com.homeflix.identity.session;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.identity.web.ApiError;
import com.homeflix.identity.web.UnauthenticatedException;

import jakarta.validation.Valid;

/**
 * Session endpoints of docs/api/identity.yaml. Public paths through the gateway:
 * /api/identity/auth/... (ADR-0017).
 */
@RestController
@RequestMapping("/auth")
public class SessionController {

	private final SessionService sessions;
	private final SessionCookies cookies;

	public SessionController(SessionService sessions, SessionCookies cookies) {
		this.sessions = sessions;
		this.cookies = cookies;
	}

	/** Anonymous. Sets the session cookie. */
	@PostMapping("/sign-in")
	public ResponseEntity<AccountView> signIn(@Valid @RequestBody Credentials credentials) {
		SignedIn signedIn = sessions.signIn(credentials);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookies.session(signedIn.token()).toString())
				.body(signedIn.account());
	}

	/** Anonymous: also works with an expired session. Clears the cookie. */
	@PostMapping("/sign-out")
	public ResponseEntity<Void> signOut() {
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookies.cleared().toString())
				.build();
	}

	/** Needs a valid, unexpired token of an enabled account. Sets a fresh cookie. */
	@PostMapping("/refresh")
	public ResponseEntity<AccountView> refresh(
			@CookieValue(name = SessionCookies.NAME, required = false) String token) {
		SignedIn signedIn = sessions.refresh(token);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookies.session(signedIn.token()).toString())
				.body(signedIn.account());
	}

	@GetMapping("/me")
	public AccountView me(@CookieValue(name = SessionCookies.NAME, required = false) String token) {
		return sessions.currentAccount(token);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ApiError> invalidCredentials() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiError("invalid_credentials", "The email or password is incorrect."));
	}

	/** The cookie is cleared too, since it no longer holds a usable session. */
	@ExceptionHandler(UnauthenticatedException.class)
	ResponseEntity<ApiError> unauthenticated() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.SET_COOKIE, cookies.cleared().toString())
				.body(new ApiError("unauthenticated", "Please sign in."));
	}

}
