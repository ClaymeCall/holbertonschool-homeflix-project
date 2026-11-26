package com.homeflix.library;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** The endpoints the security tests scan, so a new endpoint cannot be left out of them (K4, K5). */
public final class ApplicationEndpoints {

	private ApplicationEndpoints() {
	}

	/** The endpoints of HomeFlix code, as "METHOD /path". Framework ones such as /error are left out. */
	public static List<String> of(RequestMappingHandlerMapping mappings) {
		List<String> endpoints = new ArrayList<>();
		mappings.getHandlerMethods().forEach((info, handler) -> {
			if (!handler.getBeanType().getPackageName().startsWith("com.homeflix")) {
				return;
			}
			Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
			for (String path : info.getPatternValues()) {
				for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
					endpoints.add(method.name() + " " + path);
				}
			}
		});
		return endpoints;
	}

}
