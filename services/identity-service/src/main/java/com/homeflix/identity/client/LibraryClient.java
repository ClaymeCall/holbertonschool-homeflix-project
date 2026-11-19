package com.homeflix.identity.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.homeflix.identity.web.ServiceUnavailableException;

/**
 * Calls the library service's internal endpoints (ADR-0022, consistency rules). Each call carries
 * the identity service's own secret in {@code X-Service-Token}. Timeouts follow ADR-0020: 2 s to
 * connect, 5 s to read. A DELETE is never retried automatically: the user repeats it.
 */
@Component
public class LibraryClient {

	static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

	private final RestClient restClient;
	private final String serviceToken;

	public LibraryClient(
			@Value("${homeflix.library.base-url}") String baseUrl,
			@Value("${homeflix.library.service-token:}") String serviceToken,
			@Value("${homeflix.http-client.connect-timeout:2s}") Duration connectTimeout,
			@Value("${homeflix.http-client.read-timeout:5s}") Duration readTimeout) {
		if (serviceToken == null || serviceToken.isBlank()) {
			// ADR-0022: a service refuses to start without its secrets.
			throw new IllegalStateException(
					"Set IDENTITY_TO_LIBRARY_TOKEN (homeflix.library.service-token): the secret the identity service sends to the library.");
		}
		this.serviceToken = serviceToken;

		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(connectTimeout).build());
		requestFactory.setReadTimeout(readTimeout);
		this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

	/**
	 * Deletes the favourites and viewing progress of a profile ({@code 204} even when there are
	 * none). Anything but a 2xx answer, including no answer at all, is a failure.
	 *
	 * @throws ServiceUnavailableException when the library did not confirm the deletion
	 */
	public void deleteProfileData(UUID profileId) {
		try {
			restClient.delete()
					.uri("/internal/profiles/{profileId}/data", profileId)
					.header(SERVICE_TOKEN_HEADER, serviceToken)
					.retrieve()
					.toBodilessEntity();
		}
		catch (RestClientException ex) {
			throw new ServiceUnavailableException("The library did not delete the data of a profile.", ex);
		}
	}

}
