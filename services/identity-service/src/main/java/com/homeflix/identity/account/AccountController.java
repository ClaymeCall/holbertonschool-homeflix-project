package com.homeflix.identity.account;

import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.identity.security.AccessGuard;
import com.homeflix.identity.security.Caller;
import com.homeflix.identity.web.ApiError;
import com.homeflix.identity.web.InvalidInputException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Account management endpoints of docs/api/identity.yaml, all {@code x-permission: admin}
 * (ACC-09, SEC-02). Public paths through the gateway: /api/identity/accounts/... (ADR-0017).
 * Each method starts with the guard; the service then checks the caller again in the database.
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

	private static final int MAX_LIMIT = 100;

	private final AccessGuard guard;
	private final AccountManagementService accounts;

	public AccountController(AccessGuard guard, AccountManagementService accounts) {
		this.guard = guard;
		this.accounts = accounts;
	}

	@GetMapping
	public List<ManagedAccount> list(HttpServletRequest request,
			@RequestParam(name = "limit", defaultValue = "50") int limit,
			@RequestParam(name = "offset", defaultValue = "0") int offset) {
		Caller caller = guard.requireAdmin(request);
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new InvalidInputException("limit", "The limit must be between 1 and " + MAX_LIMIT + ".");
		}
		if (offset < 0) {
			throw new InvalidInputException("offset", "The offset cannot be negative.");
		}
		return accounts.list(caller, limit, offset);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ManagedAccount create(HttpServletRequest request, @Valid @RequestBody NewAccountRequest body) {
		return accounts.create(guard.requireAdmin(request), body);
	}

	@PutMapping("/{accountId}/role")
	public ManagedAccount changeRole(HttpServletRequest request, @PathVariable("accountId") UUID accountId,
			@Valid @RequestBody RoleRequest body) {
		return accounts.changeRole(guard.requireAdmin(request), accountId, body.admin());
	}

	@PostMapping("/{accountId}/disable")
	public ManagedAccount disable(HttpServletRequest request, @PathVariable("accountId") UUID accountId) {
		return accounts.setDisabled(guard.requireAdmin(request), accountId, true);
	}

	@PostMapping("/{accountId}/enable")
	public ManagedAccount enable(HttpServletRequest request, @PathVariable("accountId") UUID accountId) {
		return accounts.setDisabled(guard.requireAdmin(request), accountId, false);
	}

	/**
	 * Deletes the account and, through the library, the favourites and progress of its profiles
	 * (SEC-07, consistency rules). 503 when the library does not confirm: nothing is deleted.
	 */
	@DeleteMapping("/{accountId}")
	public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable("accountId") UUID accountId) {
		accounts.delete(guard.requireAdmin(request), accountId);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(LastAdministratorException.class)
	ResponseEntity<ApiError> lastAdministrator(LastAdministratorException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("last_administrator", ex.getMessage()));
	}

}
