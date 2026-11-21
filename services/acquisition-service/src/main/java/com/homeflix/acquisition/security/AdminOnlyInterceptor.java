package com.homeflix.acquisition.security;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs the administrator check of {@link AdminOnly} controllers before the handler's arguments are
 * resolved. The 401 and 403 it throws go through the usual exception handler.
 */
@Component
class AdminOnlyInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final AccessGuard access;

	AdminOnlyInterceptor(AccessGuard access) {
		this.access = access;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (handler instanceof HandlerMethod method && method.getBeanType().isAnnotationPresent(AdminOnly.class)) {
			request.setAttribute(AdminOnly.CALLER, access.requireAdmin(request));
		}
		return true;
	}

}
