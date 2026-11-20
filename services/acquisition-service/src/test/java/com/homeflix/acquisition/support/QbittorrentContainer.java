package com.homeflix.acquisition.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * A real qBittorrent, the image pinned in compose.yaml, started once for the test run (J5 "Done when":
 * start and cancel work on a real qBittorrent instance). Its temporary password is read from its log and
 * replaced with {@link #USERNAME} / {@link #PASSWORD}, as the HomeFlix start script does from .env (J4).
 *
 * <p>qBittorrent refuses a request whose Host header names another port than its own, so the web interface
 * listens on a free port that is published under the same number (in Compose it is qbittorrent:8080).
 */
public final class QbittorrentContainer {

	public static final String USERNAME = "homeflix-test";
	public static final String PASSWORD = "test-password-123";

	private static final String IMAGE = "lscr.io/linuxserver/qbittorrent:5.3.0_v2.1.2-ls487";
	private static final Pattern TEMPORARY_PASSWORD = Pattern.compile("session: (\\S+)");

	private static final int PORT = freePort();

	@SuppressWarnings({ "resource", "deprecation" })
	private static final FixedHostPortGenericContainer<?> CONTAINER = new FixedHostPortGenericContainer<>(IMAGE)
			.withEnv("PUID", "1000")
			.withEnv("PGID", "1000")
			.withEnv("WEBUI_PORT", String.valueOf(PORT))
			.withFixedExposedPort(PORT, PORT)
			// The web interface may still be starting after this line: the first login is retried (logInWhenReady).
			.waitingFor(Wait.forLogMessage(".*access the WebUI at.*\\n", 1));

	private static final HttpClient HTTP = HttpClient.newHttpClient();
	private static String session;
	private static boolean ready;

	private QbittorrentContainer() {
	}

	public static synchronized String url() {
		if (!ready) {
			if (!CONTAINER.isRunning()) {
				CONTAINER.start();
			}
			Matcher matcher = TEMPORARY_PASSWORD.matcher(CONTAINER.getLogs());
			if (!matcher.find()) {
				throw new IllegalStateException("qBittorrent did not print its temporary password");
			}
			session = logInWhenReady("admin", matcher.group(1));
			post("/api/v2/app/setPreferences",
					"json=" + encode("{\"web_ui_username\":\"" + USERNAME + "\",\"web_ui_password\":\"" + PASSWORD + "\"}"));
			session = logIn(USERNAME, PASSWORD);
			ready = true;
		}
		return url0();
	}

	/** True when qBittorrent has a torrent with this info hash. */
	public static boolean hasTorrent(String hash) {
		return !get("/api/v2/torrents/info?hashes=" + hash).strip().equals("[]");
	}

	/** Adds a torrent directly, as a start that was interrupted would have left it. */
	public static void addTorrent(String magnetLink) {
		post("/api/v2/torrents/add", "urls=" + encode(magnetLink));
	}

	/** Adds a torrent directly with a tag, as HomeFlix tags the torrents it adds. */
	public static void addTorrent(String magnetLink, String tag) {
		post("/api/v2/torrents/add", "urls=" + encode(magnetLink) + "&tags=" + encode(tag));
	}

	/** Removes a torrent by hand, as an administrator could in qBittorrent's own interface. */
	public static void removeTorrent(String hash) {
		post("/api/v2/torrents/delete", "hashes=" + hash + "&deleteFiles=true");
	}

	/** The details of a torrent as qBittorrent lists them (JSON), for example its tags. */
	public static String torrentInfo(String hash) {
		return get("/api/v2/torrents/info?hashes=" + hash);
	}

	private static String logIn(String username, String password) {
		HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url0() + "/api/v2/auth/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString("username=" + encode(username) + "&password=" + encode(password))));
		return response.headers().allValues("set-cookie").stream()
				.map(cookie -> cookie.split(";", 2)[0])
				.filter(cookie -> cookie.startsWith("QBT_SID") || cookie.startsWith("SID="))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("qBittorrent refused the test login (HTTP "
						+ response.statusCode() + ")"));
	}

	private static String logInWhenReady(String username, String password) {
		long deadline = System.nanoTime() + 30_000_000_000L;
		while (true) {
			try {
				return logIn(username, password);
			}
			catch (IllegalStateException ex) {
				if (System.nanoTime() > deadline) {
					throw ex;
				}
				try {
					Thread.sleep(250);
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw ex;
				}
			}
		}
	}

	private static String get(String path) {
		return send(HttpRequest.newBuilder(URI.create(url0() + path)).header("Cookie", session).GET()).body();
	}

	private static void post(String path, String form) {
		send(HttpRequest.newBuilder(URI.create(url0() + path))
				.header("Cookie", session)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form)));
	}

	private static HttpResponse<String> send(HttpRequest.Builder request) {
		try {
			return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static String url0() {
		return "http://localhost:" + PORT;
	}

	private static int freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
