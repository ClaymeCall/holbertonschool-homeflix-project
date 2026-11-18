package com.homeflix.library.web;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses state-changing requests that a browser marks as coming from another site
 * (Fetch Metadata header {@code Sec-Fetch-Site}). A second line of defence against CSRF,
 * since SameSite=Strict is only a mitigation (ADR-0009, R-09).
 *
 * HomeFlix is served from one origin through the gateway, so its own pages always send
 * {@code same-origin}. Requests without the header (older browsers, curl, tests) are let through:
 * they are covered by SameSite=Strict and the JSON-only bodies.
 */
@Component
public class CrossSiteRequestFilter extends OncePerRequestFilter {

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
	private static final Set<String> FOREIGN_SITES = Set.of("cross-site", "same-site");

	private static final String BODY =
			"{\"error\":\"forbidden\",\"message\":\"You do not have permission to do this.\"}";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String site = request.getHeader("Sec-Fetch-Site");
		if (!SAFE_METHODS.contains(request.getMethod()) && site != null && FOREIGN_SITES.contains(site)) {
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(BODY);
			return;
		}
		chain.doFilter(request, response);
	}

}
