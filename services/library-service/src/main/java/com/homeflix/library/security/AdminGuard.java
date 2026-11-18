package com.homeflix.library.security;

import org.springframework.stereotype.Component;

import com.homeflix.library.web.ForbiddenException;
import com.homeflix.library.web.UnauthenticatedException;

import jakarta.servlet.http.HttpServletRequest;

/** Server-side check for administrator-only operations (ACC-06, SEC-02). */
@Component
public class AdminGuard {

	private final CallerProvider callers;

	public AdminGuard(CallerProvider callers) {
		this.callers = callers;
	}

	/**
	 * @throws UnauthenticatedException 401 when nobody is signed in
	 * @throws ForbiddenException 403 when the caller is not an administrator
	 */
	public Caller requireAdmin(HttpServletRequest request) {
		Caller caller = callers.current(request).orElseThrow(UnauthenticatedException::new);
		if (!caller.admin()) {
			throw new ForbiddenException();
		}
		return caller;
	}

}
