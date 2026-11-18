package com.homeflix.library.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.homeflix.library.demo.DemoController;
import com.homeflix.library.demo.IdentityClient;
import com.homeflix.library.web.ApiExceptionHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Timeouts, retries and error answers against a real stub HTTP server that can be healthy, slow,
 * failing or flaky (R-24, TST-05). Short timeouts keep the tests fast; the policy is the same
 * as the defaults (connect and read timeout, one retry of a GET).
 */
class ServiceCallResilienceTest {

	private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(300);
	private static final Duration READ_TIMEOUT = Duration.ofMillis(400);

	private HttpServer stub;
	private ExecutorService stubThreads;
	private final AtomicInteger requests = new AtomicInteger();
	/** Decides the answer for the nth request (1-based): a status code, or 0 to answer slowly. */
	private volatile IntFunction<Integer> behaviour = n -> 200;

	private final ServiceClientFactory factory =
			new ServiceClientFactory(CONNECT_TIMEOUT, READ_TIMEOUT, 1, Duration.ofMillis(50));

	@BeforeEach
	void startStub() throws IOException {
		stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		stub.createContext("/", exchange -> {
			int n = requests.incrementAndGet();
			int code = behaviour.apply(n);
			try {
				if (code == 0) {
					Thread.sleep(READ_TIMEOUT.toMillis() * 3);
					code = 200;
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			byte[] body = "{\"service\":\"identity-service\",\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
			try {
				exchange.sendResponseHeaders(code, code == 200 ? body.length : -1);
				if (code == 200) {
					try (OutputStream out = exchange.getResponseBody()) {
						out.write(body);
					}
				}
			}
			catch (IOException ignored) {
				// The client gave up on a slow answer.
			}
			finally {
				exchange.close();
			}
		});
		// Several threads, so a retry is not queued behind a slow first request.
		stubThreads = Executors.newCachedThreadPool();
		stub.setExecutor(stubThreads);
		stub.start();
	}

	@AfterEach
	void stopStub() {
		stub.stop(0);
		stubThreads.shutdownNow();
	}

	private String baseUrl() {
		return "http://127.0.0.1:" + stub.getAddress().getPort();
	}

	private MockMvc demoEndpoint(String baseUrl) {
		IdentityClient identity = new IdentityClient(factory.create(baseUrl));
		return MockMvcBuilders.standaloneSetup(new DemoController(identity))
				.setControllerAdvice(new ApiExceptionHandler()).build();
	}

	// ---- Retry policy ----

	@Test
	void aGetIsRetriedOnceAfter503AndThenSucceeds() throws Exception {
		behaviour = n -> n == 1 ? 503 : 200;

		demoEndpoint(baseUrl()).perform(get("/demo/identity"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.callee.service").value("identity-service"));
		assertThat(requests.get()).isEqualTo(2);
	}

	@Test
	void aGetIsRetriedOnlyOnce() {
		behaviour = n -> 503;
		RestClient client = factory.create(baseUrl());

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> client.get().uri("/ping").retrieve().toBodilessEntity()))
				.isInstanceOf(HttpServerErrorException.class);
		assertThat(requests.get()).isEqualTo(2);
	}

	@Test
	void a502And504AreRetriedToo() {
		behaviour = n -> n == 1 ? 502 : n == 2 ? 504 : 200;
		RestClient client = factory.create(baseUrl());
		// 1 retry only: 502, then 504 is returned as the final answer.
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> client.get().uri("/ping").retrieve().toBodilessEntity()))
				.isInstanceOf(HttpServerErrorException.class);
		assertThat(requests.get()).isEqualTo(2);
	}

	@Test
	void aPostIsNeverRetried() {
		behaviour = n -> 503;
		RestClient client = factory.create(baseUrl());

		assertThat(org.assertj.core.api.Assertions.catchThrowable(
				() -> client.post().uri("/ping").body("{}").retrieve().toBodilessEntity()))
				.isInstanceOf(HttpServerErrorException.class);
		assertThat(requests.get()).isEqualTo(1);
	}

	@Test
	void a4xxIsNeverRetried() {
		behaviour = n -> 404;
		RestClient client = factory.create(baseUrl());

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> client.get().uri("/ping").retrieve().toBodilessEntity()))
				.isInstanceOf(HttpClientErrorException.class);
		assertThat(requests.get()).isEqualTo(1);
	}

	// ---- Failing or slow dependency gives the common 503 (R-24) ----

	@Test
	void aDependencyThatKeepsFailingGivesThe503Body() throws Exception {
		behaviour = n -> 500;

		demoEndpoint(baseUrl()).perform(get("/demo/identity"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"))
				.andExpect(jsonPath("$.message").value(UNAVAILABLE_MESSAGE))
				.andExpect(content().string(not(containsString("127.0.0.1"))));
	}

	@Test
	void aSlowDependencyTimesOutAndGivesThe503BodyWithinABoundedTime() throws Exception {
		behaviour = n -> 0;

		long start = System.nanoTime();
		demoEndpoint(baseUrl()).perform(get("/demo/identity"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"));
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

		// Two attempts of at most the read timeout, plus the pause: never an unbounded wait.
		assertThat(requests.get()).isEqualTo(2);
		assertThat(elapsed).isLessThan(READ_TIMEOUT.multipliedBy(2).plusSeconds(1));
	}

	@Test
	void aDependencyThatIsDownGivesThe503Body() throws Exception {
		stub.stop(0);

		demoEndpoint(baseUrl()).perform(get("/demo/identity"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"))
				.andExpect(content().string(not(containsString("Connection refused"))));
	}

	@Test
	void aConnectionFailureSurfacesAsAResourceAccessException() {
		stub.stop(0);
		RestClient client = factory.create(baseUrl());

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> client.get().uri("/ping").retrieve().toBodilessEntity()))
				.isInstanceOf(ResourceAccessException.class);
	}

	private static final String UNAVAILABLE_MESSAGE =
			"This part of HomeFlix is temporarily unavailable. Please try again in a moment.";

}
