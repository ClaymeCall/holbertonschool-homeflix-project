package com.homeflix.acquisition.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/**
 * Stands in for the library's internal download endpoints (J8 builds the real ones). Records every call,
 * with its token, and answers with the status the test chooses. {@link #stop()} makes it unreachable.
 */
public final class LibraryStub {

	/** One call received: method, path, X-Service-Token header and body. */
	public record Call(String method, String path, String token, String body) {
	}

	private final HttpServer server;
	private final List<Call> calls = new CopyOnWriteArrayList<>();
	private volatile int startStatus = 200;
	private volatile int failedStatus = 204;

	public LibraryStub() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		server.createContext("/", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String path = exchange.getRequestURI().getPath();
			calls.add(new Call(exchange.getRequestMethod(), path, exchange.getRequestHeaders().getFirst("X-Service-Token"), body));
			int status = path.endsWith("/failed") ? failedStatus : startStatus;
			byte[] answer = status == 409
					? "{\"error\":\"already_in_library\",\"message\":\"This title is already in the library.\"}"
							.getBytes(StandardCharsets.UTF_8)
					: new byte[0];
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, answer.length == 0 ? -1 : answer.length);
			if (answer.length > 0) {
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(answer);
				}
			}
			exchange.close();
		});
		server.start();
	}

	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public List<Call> calls() {
		return List.copyOf(calls);
	}

	/** Forgets the calls and answers normally again. */
	public void reset() {
		calls.clear();
		startStatus = 200;
		failedStatus = 204;
	}

	public void answerStartWith(int status) {
		startStatus = status;
	}

	public void answerFailedWith(int status) {
		failedStatus = status;
	}

	public void stop() {
		server.stop(0);
	}

}
