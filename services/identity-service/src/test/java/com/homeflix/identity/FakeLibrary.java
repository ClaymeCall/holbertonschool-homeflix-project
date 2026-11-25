package com.homeflix.identity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Stands for the library service in the tests: a small HTTP server from the JDK, started once,
 * that records the internal calls it receives and answers with the status a test chooses.
 * The real endpoint comes with K3; this checks the identity side of the consistency rule.
 */
public final class FakeLibrary {

	/** The secret the identity service is configured with in the tests. Not a real secret. */
	public static final String SERVICE_TOKEN = "test-identity-to-library-token";

	private static final FakeLibrary INSTANCE = new FakeLibrary();

	/** A call received: method, path and the X-Service-Token header. */
	public record Call(String method, String path, String serviceToken) {
	}

	private final HttpServer server;
	private final List<Call> calls = new CopyOnWriteArrayList<>();
	private volatile int status = 204;
	private volatile int successesBeforeStatus = 0;
	private volatile Runnable onNextCall;

	private FakeLibrary() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		server.createContext("/", this::handle);
		server.start();
	}

	public static FakeLibrary instance() {
		return INSTANCE;
	}

	public String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** Every following call is answered with this status, without a body. */
	public void answerWith(int status) {
		answerWith(status, 0);
	}

	/**
	 * The next {@code successes} calls are answered 204, and every one after them with this status:
	 * a library that fails in the middle of a series of calls.
	 */
	public void answerWith(int status, int successes) {
		this.successesBeforeStatus = calls.size() + successes;
		this.status = status;
	}

	/** Runs this once, when the next call arrives and before it is answered. */
	public void onNextCall(Runnable action) {
		this.onNextCall = action;
	}

	/** Back to 204, no pending action and no recorded call. */
	public void reset() {
		status = 204;
		successesBeforeStatus = 0;
		onNextCall = null;
		calls.clear();
	}

	public List<Call> calls() {
		return List.copyOf(calls);
	}

	private synchronized void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			Runnable action = onNextCall;
			onNextCall = null;
			if (action != null) {
				action.run();
			}
			calls.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
					exchange.getRequestHeaders().getFirst("X-Service-Token")));
			exchange.sendResponseHeaders(calls.size() > successesBeforeStatus ? status : 204, -1);
		}
	}

}
