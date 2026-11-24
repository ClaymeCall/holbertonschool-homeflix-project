package com.homeflix.acquisition.library;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.homeflix.acquisition.client.ServiceClientFactory;
import com.homeflix.acquisition.config.LibraryProperties;
import com.homeflix.acquisition.web.RejectedRequestException;
import com.homeflix.acquisition.web.ServiceUnavailableException;

/**
 * Calls the library's internal download endpoints (docs/architecture/consistency-rules.md, "Acquisition
 * handoff"; ADR-0022). Each call carries this service's {@code X-Service-Token}. The library answers them
 * only on {@code /internal}, which the gateway blocks.
 */
@Component
public class LibraryClient {

	static final String TOKEN_HEADER = "X-Service-Token";
	static final String ALREADY_IN_LIBRARY = "already_in_library";

	private static final Logger log = LoggerFactory.getLogger(LibraryClient.class);

	private final RestClient rest;

	/** The code of the library's error body (common.yaml). Other fields are ignored. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record LibraryError(String error) {
	}

	public LibraryClient(ServiceClientFactory clients, LibraryProperties library) {
		this.rest = clients.create(library.baseUrl()).mutate()
				.defaultHeader(TOKEN_HEADER, library.serviceToken())
				.build();
	}

	/**
	 * Tells the library a download of this entry starts, so its catalogue shows the title as downloading
	 * before the torrent is added. Repeating it changes nothing (consistency rules, "Starting").
	 *
	 * @throws RejectedRequestException 409 {@code already_in_library} when the title is already available. A
	 *         409 with another code is not one the consistency rules describe, and is handled as below.
	 * @throws ServiceUnavailableException when the library does not answer or refuses the call for any other
	 *         reason: the download must not start
	 */
	public void downloadStarting(UUID entryId, String title) {
		try {
			rest.put().uri("/internal/downloads/{entryId}", entryId)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("title", title))
					.retrieve()
					.toBodilessEntity();
		}
		catch (HttpClientErrorException.Conflict ex) {
			if (!ALREADY_IN_LIBRARY.equals(errorCode(ex))) {
				// Not a conflict the consistency rules describe: the library and this service disagree.
				log.error("The library refused the start of a download for entry {} with an unexpected conflict",
						entryId, ex);
				throw new ServiceUnavailableException("Library refused a download start", ex);
			}
			throw new RejectedRequestException(HttpStatus.CONFLICT, ALREADY_IN_LIBRARY,
					"This title is already in the library.");
		}
		catch (RestClientException ex) {
			// A 401 or 404 here is a configuration problem (token, or a library without J8), not the user's.
			log.error("The library refused or did not answer the start of a download for entry {}", entryId, ex);
			throw new ServiceUnavailableException("Library unavailable for a download start", ex);
		}
	}

	/**
	 * Tells the library a download failed or was cancelled, so its entry is no longer shown as downloading
	 * (consistency rules, "Failing and cancelling").
	 *
	 * @return false when the library could not be told: the call must be repeated later
	 */
	public boolean downloadFailed(UUID entryId) {
		try {
			rest.post().uri("/internal/downloads/{entryId}/failed", entryId).retrieve().toBodilessEntity();
			return true;
		}
		catch (RestClientException ex) {
			log.warn("The library could not be told that the download of entry {} failed; it will be told later",
					entryId, ex);
			return false;
		}
	}

	/** The error code of a refusal, or null when its body is not the common error body. */
	private static String errorCode(HttpClientErrorException ex) {
		try {
			LibraryError body = ex.getResponseBodyAs(LibraryError.class);
			return body == null ? null : body.error();
		}
		catch (RuntimeException unreadable) {
			return null;
		}
	}

}
