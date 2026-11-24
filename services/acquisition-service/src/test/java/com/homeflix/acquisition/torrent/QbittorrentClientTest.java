package com.homeflix.acquisition.torrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.homeflix.acquisition.client.ServiceClientFactory;
import com.homeflix.acquisition.config.QbittorrentProperties;
import com.homeflix.acquisition.web.ServiceUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The session handling of the client, against a stand-in that answers as the test chooses: a 403 to a
 * request means a lost session and is retried once after a new login, a refused login is never retried.
 */
class QbittorrentClientTest {

	private static final String HASH = "a".repeat(40);

	private final List<String> paths = new CopyOnWriteArrayList<>();
	private HttpServer server;

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void recognisesTheSessionCookieOfEachVersion() {
		assertThat(QbittorrentClient.isSessionCookie("QBT_SID_8080=abc")).isTrue();
		assertThat(QbittorrentClient.isSessionCookie("SID=abc")).isTrue();
		assertThat(QbittorrentClient.isSessionCookie("SIDE=abc")).isFalse();
		assertThat(QbittorrentClient.isSessionCookie("other=abc")).isFalse();
	}

	@Test
	void aLostSessionIsOpenedAgainOnce() {
		AtomicInteger deletes = new AtomicInteger();
		QbittorrentClient client = clientOf(exchange -> {
			if (exchange.getRequestURI().getPath().equals("/api/v2/auth/login")) {
				exchange.getResponseHeaders().add("Set-Cookie", "QBT_SID_8080=s; HttpOnly; path=/");
				answer(exchange, 200);
			}
			else {
				answer(exchange, deletes.incrementAndGet() == 1 ? 403 : 200);
			}
		});

		client.delete(HASH);

		assertThat(paths).containsExactly("/api/v2/auth/login", "/api/v2/torrents/delete", "/api/v2/auth/login",
				"/api/v2/torrents/delete");
	}

	@Test
	void aRefusedLoginIsNotRepeated() {
		// qBittorrent answers 403 to a login from an address it has banned after failed logins.
		QbittorrentClient client = clientOf(exchange -> answer(exchange, 403));

		assertThatThrownBy(() -> client.delete(HASH)).isInstanceOf(ServiceUnavailableException.class);

		assertThat(paths).containsExactly("/api/v2/auth/login");
	}

	@Test
	void aTorrentTheClientDidNotAddIsAFailure() {
		// 409 is the answer both to a torrent already there and to a refused link: only the list decides.
		QbittorrentClient client = clientOf(exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/api/v2/auth/login")) {
				exchange.getResponseHeaders().add("Set-Cookie", "QBT_SID_8080=s; HttpOnly; path=/");
				answer(exchange, 200);
			}
			else if (path.equals("/api/v2/torrents/add")) {
				answer(exchange, 409);
			}
			else {
				json(exchange, "[]");
			}
		});

		assertThatThrownBy(() -> client.add("magnet:?xt=urn:btih:" + HASH, HASH, "homeflix-job-x"))
				.isInstanceOf(ServiceUnavailableException.class);
	}

	@Test
	void aTorrentThatAppearsAShortTimeAfterTheAddIsAdded() {
		// qBittorrent 5 adds a magnet link in the background: its answer can come before the torrent is listed.
		AtomicInteger reads = new AtomicInteger();
		QbittorrentClient client = clientOf(exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/api/v2/auth/login")) {
				exchange.getResponseHeaders().add("Set-Cookie", "QBT_SID_8080=s; HttpOnly; path=/");
				answer(exchange, 200);
			}
			else if (path.equals("/api/v2/torrents/add")) {
				answer(exchange, 200);
			}
			else {
				json(exchange, reads.incrementAndGet() < 3 ? "[]" : "[{\"hash\":\"" + HASH + "\"}]");
			}
		});

		client.add("magnet:?xt=urn:btih:" + HASH, HASH, "homeflix-job-x");

		assertThat(reads).hasValue(3);
	}

	private interface Handler {
		void handle(HttpExchange exchange) throws IOException;
	}

	private QbittorrentClient clientOf(Handler handler) {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		server.createContext("/", exchange -> {
			exchange.getRequestBody().readAllBytes();
			paths.add(exchange.getRequestURI().getPath());
			handler.handle(exchange);
		});
		server.start();
		ServiceClientFactory clients = new ServiceClientFactory(Duration.ofSeconds(1), Duration.ofSeconds(1), 0,
				Duration.ZERO);
		// A short wait for an added torrent to appear, so a torrent that never does fails quickly.
		return new QbittorrentClient(clients,
				new QbittorrentProperties("http://127.0.0.1:" + server.getAddress().getPort(), "user", "password"),
				Duration.ofMillis(300), Duration.ofMillis(10));
	}

	private static void json(HttpExchange exchange, String json) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		byte[] body = json.getBytes();
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private static void answer(HttpExchange exchange, int status) throws IOException {
		exchange.sendResponseHeaders(status, -1);
		exchange.close();
	}

}
