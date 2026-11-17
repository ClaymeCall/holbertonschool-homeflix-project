package com.homeflix.library.demo;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

/**
 * Demonstrates one service calling another (issue #27, ARC-10, ARC-08):
 * {@code GET /api/library/demo/identity} through the gateway makes the library service
 * call the identity service. Temporary: remove it once real inter-service calls exist (G5, #43).
 */
@RestController
public class DemoController {

	private static final Logger log = LoggerFactory.getLogger(DemoController.class);

	private final IdentityClient identityClient;

	public DemoController(IdentityClient identityClient) {
		this.identityClient = identityClient;
	}

	@GetMapping("/demo/identity")
	public ResponseEntity<Map<String, Object>> callIdentity() {
		try {
			IdentityClient.Ping ping = identityClient.ping();
			return ResponseEntity.ok(Map.of("caller", "library-service", "callee", ping));
		}
		catch (RestClientException e) {
			log.warn("Identity service call failed", e);
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
					"error", "service_unavailable",
					"message", "This part of HomeFlix is temporarily unavailable. Please try again in a moment."));
		}
	}

}
