package com.homeflix.acquisition.torrent;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.homeflix.acquisition.client.ServiceClientFactory;
import com.homeflix.acquisition.config.QbittorrentProperties;
import com.homeflix.acquisition.web.ServiceUnavailableException;

/**
 * The torrent client's Web API (qBittorrent 5, API v2; ADR-0014). qBittorrent only transfers data: the
 * business rules (verified catalogue, administrator only, job states) stay in this service.
 *
 * <p>The session is the cookie returned by the login: {@code QBT_SID_<port>} since qBittorrent 5, {@code SID}
 * before. It is sent back exactly as received. When qBittorrent forgets it (restart, expiry) it answers 403,
 * and the call is made again once after a new login.
 *
 * <p>Any failure (client down, login refused, source refused) is a {@link ServiceUnavailableException}:
 * the administrator sees that the download service is unavailable, the detail goes to the log (SEC-04).
 */
@Component
public class QbittorrentClient {

	private static final Logger log = LoggerFactory.getLogger(QbittorrentClient.class);


	private final RestClient rest;
	private final QbittorrentProperties settings;

	public QbittorrentClient(ServiceClientFactory clients, QbittorrentProperties settings) {
		this.rest = clients.create(settings.url());
		this.settings = settings;
	}

	/** The session cookie as received, {@code name=value}. */
	private volatile String session;

	/** Torrent added by {@link #add}: its v1 info hash, lower case. */
	public record Added(String hash) {
	}

	/** The answer of qBittorrent 5 to an add: the ids (info hashes) of the torrents it added. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record AddResponse(List<String> added_torrent_ids) {
	}

	/**
	 * Adds a torrent from a catalogue entry's download source (a magnet link or an https .torrent address,
	 * never from a user), tagged with the given tag so it can be recognised in the client.
	 */
	public Added add(String downloadSource, String tag) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("urls", downloadSource);
		form.add("tags", tag);
		AddResponse response = call("add a torrent", sid -> rest.post().uri("/api/v2/torrents/add")
				.header(HttpHeaders.COOKIE, sid)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(AddResponse.class));
		if (response == null || response.added_torrent_ids() == null || response.added_torrent_ids().size() != 1) {
			throw new ServiceUnavailableException("The torrent client did not report the added torrent", null);
		}
		return new Added(response.added_torrent_ids().get(0).toLowerCase(Locale.ROOT));
	}

	/**
	 * Removes a torrent and the data it downloaded. Removing a torrent the client does not know is not an
	 * error: the result is the same.
	 */
	public void delete(String hash) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("hashes", hash);
		form.add("deleteFiles", "true");
		call("remove a torrent", sid -> rest.post().uri("/api/v2/torrents/delete")
				.header(HttpHeaders.COOKIE, sid)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity());
	}

	private <T> T call(String what, Function<String, T> request) {
		try {
			try {
				return request.apply(currentSession());
			}
			catch (HttpClientErrorException.Forbidden ex) {
				// The session expired or the client restarted: log in again, once.
				session = null;
				return request.apply(currentSession());
			}
		}
		catch (RestClientException ex) {
			log.error("The torrent client could not {}", what, ex);
			throw new ServiceUnavailableException("Torrent client unavailable", ex);
		}
	}

	private synchronized String currentSession() {
		if (session == null) {
			session = logIn();
		}
		return session;
	}

	private String logIn() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("username", settings.username());
		form.add("password", settings.password());
		ResponseEntity<Void> response;
		try {
			response = rest.post().uri("/api/v2/auth/login")
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.toBodilessEntity();
		}
		catch (HttpClientErrorException.Unauthorized ex) {
			// Never logs the password: only that the login from .env does not match the client's.
			log.error("The torrent client refused the login of QBITTORRENT_USERNAME / QBITTORRENT_PASSWORD");
			throw ex;
		}
		return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
				.map(cookie -> cookie.split(";", 2)[0].strip())
				.filter(QbittorrentClient::isSessionCookie)
				.findFirst()
				.orElseThrow(() -> new RestClientException("The torrent client did not open a session"));
	}

	/** {@code QBT_SID_<port>=...} (qBittorrent 5) or {@code SID=...} (earlier versions). */
	static boolean isSessionCookie(String nameAndValue) {
		String name = nameAndValue.split("=", 2)[0];
		return name.equals("SID") || name.startsWith("QBT_SID");
	}

}
