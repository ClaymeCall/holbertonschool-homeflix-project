package com.homeflix.identity.account;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.identity.security.AccessGuard;
import com.homeflix.identity.session.SessionCookies;
import com.homeflix.identity.web.ApiError;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code DELETE /auth/me} of docs/api/identity.yaml, {@code x-permission: account} (SEC-07): the
 * signed-in account deletes itself. The account always comes from the token, never from the
 * request (R-06). Public path through the gateway: /api/identity/auth/me (ADR-0017).
 */
@RestController
@RequestMapping("/auth/me")
public class OwnAccountController {

	private final AccessGuard guard;
	private final AccountManagementService accounts;
	private final SessionCookies cookies;

	public OwnAccountController(AccessGuard guard, AccountManagementService accounts, SessionCookies cookies) {
		this.guard = guard;
		this.accounts = accounts;
		this.cookies = cookies;
	}

	/** Clears the session cookie: the account it belonged to is gone. */
	@DeleteMapping
	public ResponseEntity<Void> delete(HttpServletRequest request) {
		accounts.deleteOwn(guard.requireAccount(request));
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookies.cleared().toString())
				.build();
	}

	@ExceptionHandler(LastAdministratorException.class)
	ResponseEntity<ApiError> lastAdministrator(LastAdministratorException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("last_administrator", ex.getMessage()));
	}

}
