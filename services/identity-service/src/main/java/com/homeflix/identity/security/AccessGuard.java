package com.homeflix.identity.security;

import org.springframework.stereotype.Component;

import com.homeflix.identity.web.ForbiddenException;
import com.homeflix.identity.web.UnauthenticatedException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Server-side permission checks (ACC-06, SEC-02), the same in every service. A protected
 * controller method starts with one of these calls; the interface only reflects them.
 */
@Component
public class AccessGuard {

	private final CallerProvider callers;

	public AccessGuard(CallerProvider callers) {
		this.callers = callers;
	}

	/**
	 * Any signed-in account (the {@code account} level of the permission matrix).
	 *
	 * @throws UnauthenticatedException 401 when nobody is signed in
	 */
	public Caller requireAccount(HttpServletRequest request) {
		return callers.current(request).orElseThrow(UnauthenticatedException::new);
	}

	/**
	 * An administrator.
	 *
	 * @throws UnauthenticatedException 401 when nobody is signed in
	 * @throws ForbiddenException 403 when the caller is not an administrator
	 */
	public Caller requireAdmin(HttpServletRequest request) {
		Caller caller = requireAccount(request);
		if (!caller.admin()) {
			throw new ForbiddenException();
		}
		return caller;
	}

}
