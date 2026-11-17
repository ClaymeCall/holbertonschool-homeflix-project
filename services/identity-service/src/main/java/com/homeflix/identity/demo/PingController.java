package com.homeflix.identity.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiny endpoint other services call to prove service-to-service REST works (issue #27, ADR-0008).
 * Returns no data about accounts. To be replaced by the real inter-service API (G5, #43).
 */
@RestController
public class PingController {

	public record Ping(String service, String status) {
	}

	@GetMapping("/ping")
	public Ping ping() {
		return new Ping("identity-service", "ok");
	}

}
