package com.homeflix.library.demo;

import org.springframework.web.client.RestClient;

/**
 * Calls the identity service's REST API with Spring's {@link RestClient} (ADR-0008).
 * Demo client for issue #27: the shared timeout, retry and error conventions come with issue #36.
 */
public class IdentityClient {

	public record Ping(String service, String status) {
	}

	private final RestClient restClient;

	public IdentityClient(RestClient restClient) {
		this.restClient = restClient;
	}

	public Ping ping() {
		return restClient.get().uri("/ping").retrieve().body(Ping.class);
	}

}
