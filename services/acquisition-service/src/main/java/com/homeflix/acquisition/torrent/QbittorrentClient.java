package com.homeflix.acquisition.torrent;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
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
 * and the call is made again once after a new login. A torrent is named by the info hash of its magnet link.
 *
 * <p>Any failure (client down, login refused, torrent not added) is a {@link ServiceUnavailableException}:
 * the administrator sees that the download service is unavailable, the detail goes to the log (SEC-04).
 */
@Component
public class QbittorrentClient {

	private static final Logger log = LoggerFactory.getLogger(QbittorrentClient.class);

	/** How long an added torrent may take to appear in the client's list, and how often the list is read. */
	private static final Duration ADD_WAIT = Duration.ofSeconds(5);
	private static final Duration ADD_POLL = Duration.ofMillis(100);

	private final RestClient rest;
	private final QbittorrentProperties settings;
	private final Duration addWait;
	private final Duration addPoll;

	/** The session cookie as received, {@code name=value}. */
	private volatile String session;

	@Autowired
	public QbittorrentClient(ServiceClientFactory clients, QbittorrentProperties settings) {
		this(clients, settings, ADD_WAIT, ADD_POLL);
	}

	QbittorrentClient(ServiceClientFactory clients, QbittorrentProperties settings, Duration addWait,
			Duration addPoll) {
		this.rest = clients.create(settings.url());
		this.settings = settings;
		this.addWait = addWait;
		this.addPoll = addPoll;
	}

	/**
	 * Adds the torrent of a catalogue entry's magnet link (never from a user), tagged with the given tag so it
	 * can be recognised in the client, and checks that the client now has the torrent with that info hash.
	 *
	 * <p>qBittorrent answers 409 both when the torrent is already there and when it refuses the link, so the
	 * answer is not trusted: only the presence of the info hash is. A torrent already there is one an earlier
	 * start left behind (no active job has this hash, the database makes sure of it), and is kept.
	 *
	 * <p>qBittorrent answers before the torrent is in its list (it is added in the background), so the list
	 * is read again for a few seconds before the torrent counts as not added.
	 *
	 * @throws ServiceUnavailableException when the client is down or does not have the torrent afterwards
	 */
	public void add(String magnetLink, String infoHash, String tag) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("urls", magnetLink);
		form.add("tags", tag);
		call("add a torrent", sid -> {
			try {
				return rest.post().uri("/api/v2/torrents/add")
						.header(HttpHeaders.COOKIE, sid)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.body(form)
						.retrieve()
						.toBodilessEntity();
			}
			catch (HttpClientErrorException.Conflict ex) {
				return null; // Already there, or refused: the check below tells which.
			}
		});
		if (!appears(infoHash)) {
			log.error("The torrent client did not add the torrent {}", infoHash);
			throw new ServiceUnavailableException("The torrent client did not add the torrent", null);
		}
	}

	/**
	 * A torrent as the client lists it: info hash, state, progress from 0 to 1, and its tags separated by
	 * commas.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Torrent(String hash, String state, double progress, String tags) {
	}

	/**
	 * Every torrent of the client, for the status check (J6).
	 *
	 * @throws ServiceUnavailableException when the client is down
	 */
	public List<Torrent> list() {
		List<Torrent> torrents = call("list the torrents", sid -> rest.get()
				.uri("/api/v2/torrents/info")
				.header(HttpHeaders.COOKIE, sid)
				.retrieve()
				.body(new ParameterizedTypeReference<List<Torrent>>() {
				}));
		return torrents == null ? List.of() : torrents;
	}

	/** True once the client lists the torrent, false if it does not within {@link #addWait}. */
	private boolean appears(String infoHash) {
		long deadline = System.nanoTime() + addWait.toNanos();
		while (!has(infoHash)) {
			if (System.nanoTime() >= deadline) {
				return false;
			}
			try {
				Thread.sleep(addPoll);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return true;
	}

	/** True when the client has a torrent with this info hash. */
	boolean has(String infoHash) {
		List<?> torrents = call("list a torrent", sid -> rest.get()
				.uri("/api/v2/torrents/info?hashes={hash}", infoHash)
				.header(HttpHeaders.COOKIE, sid)
				.retrieve()
				.body(List.class));
		return torrents != null && !torrents.isEmpty();
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

	/**
	 * Makes a request with the session, logging in first if there is none. A 403 from the request means the
	 * session is no longer valid: the login and the request are made again, once. A refused login is never
	 * repeated, so a client that has banned this address after failed logins is not asked again.
	 */
	private <T> T call(String what, Function<String, T> request) {
		try {
			String sid = currentSession();
			try {
				return request.apply(sid);
			}
			catch (HttpClientErrorException.Forbidden ex) {
				// The session expired or the client restarted: log in again, once.
				forget(sid);
				return request.apply(currentSession());
			}
		}
		catch (RestClientException ex) {
			log.error("The torrent client could not {}", what, ex);
			throw new ServiceUnavailableException("Torrent client unavailable", ex);
		}
	}

	/** Forgets this session, unless another request has already replaced it. */
	private synchronized void forget(String sid) {
		if (sid.equals(session)) {
			session = null;
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
