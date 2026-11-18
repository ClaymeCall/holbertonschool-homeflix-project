package com.homeflix.library.security;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.homeflix.library.web.ForbiddenException;
import com.homeflix.library.web.ProfileNotSelectedException;
import com.homeflix.library.web.UnauthenticatedException;

import jakarta.servlet.http.HttpServletRequest;

/** Server-side permission checks (ACC-06, SEC-02). The interface only reflects them. */
@Component
public class AccessGuard {

	private final CallerProvider callers;

	public AccessGuard(CallerProvider callers) {
		this.callers = callers;
	}

	/**
	 * Any signed-in account (the {@code account} level of docs/api/library.yaml).
	 *
	 * @throws UnauthenticatedException 401 when nobody is signed in
	 */
	public Caller requireAccount(HttpServletRequest request) {
		return callers.current(request).orElseThrow(UnauthenticatedException::new);
	}

	/**
	 * The profile chosen in this session, for what belongs to a profile (favourites, progress). It comes from
	 * the signed token only, never from the request (PRF-04, R-06).
	 *
	 * @throws UnauthenticatedException 401 when nobody is signed in
	 * @throws ProfileNotSelectedException 403 when the token carries no profile
	 */
	public UUID requireProfile(HttpServletRequest request) {
		UUID profileId = requireAccount(request).profileId();
		if (profileId == null) {
			throw new ProfileNotSelectedException();
		}
		return profileId;
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
