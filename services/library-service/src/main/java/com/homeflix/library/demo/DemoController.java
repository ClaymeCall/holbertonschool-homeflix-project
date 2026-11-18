package com.homeflix.library.demo;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates one service calling another (issue #27, ARC-10, ARC-08):
 * {@code GET /api/library/demo/identity} through the gateway makes the library service
 * call the identity service. A failing or slow identity service gives the common 503 through
 * the shared error handler. Temporary: remove it once real inter-service calls exist (G5, #43).
 */
@RestController
public class DemoController {

	private final IdentityClient identityClient;

	public DemoController(IdentityClient identityClient) {
		this.identityClient = identityClient;
	}

	@GetMapping("/demo/identity")
	public Map<String, Object> callIdentity() {
		return Map.of("caller", "library-service", "callee", identityClient.ping());
	}

}
