package com.homeflix.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntPredicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.homeflix.acquisition.security.TestTokens;

import jakarta.servlet.http.Cookie;

/**
 * Every endpoint of the acquisition service is for administrators only, and refuses everybody else, with real
 * signed session tokens (K4; SEC-02, SEC-10, ACQ-05; R-12). The endpoints are found by scanning the application,
 * so a new endpoint that is missing from {@link #ENDPOINTS}, or not covered by {@code @AdminOnly}, fails this
 * test. Each request carries a valid body, so a refusal comes from the guard and not from validation.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EndpointPermissionTests {

	@DynamicPropertySource
	static void identityPublicKey(DynamicPropertyRegistry registry) {
		registry.add("homeflix.security.public-key-file",
				() -> TestTokens.writeTemporaryPem(TestTokens.toPem(TestTokens.identityPublicKey())));
	}

	/** Every endpoint, all "admin" in {@code docs/api/acquisition.yaml}. */
	private static final List<String> ENDPOINTS = List.of(
			"GET /entries",
			"GET /entries/{entryId}",
			"GET /downloads",
			"POST /downloads",
			"GET /downloads/{downloadId}",
			"POST /downloads/{downloadId}/cancel");

	private static final Map<String, String> BODIES = Map.of(
			"POST /downloads", "{\"entryId\":\"" + UUID.randomUUID() + "\"}");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void everyEndpointIsListed() {
		assertThat(new TreeSet<>(ApplicationEndpoints.of(mappings))).isEqualTo(new TreeSet<>(ENDPOINTS));
	}

	@Test
	void anonymousCallersAreRefusedEverywhere() throws Exception {
		expect(request -> request, 401);
		expect(request -> request.cookie(new Cookie("homeflix_token", "not-a-token")), 401);
	}

	@Test
	void aSignedInUserIsRefusedEverywhere() throws Exception {
		String user = TestTokens.user(UUID.randomUUID(), UUID.randomUUID());
		expect(request -> request.cookie(new Cookie("homeflix_token", user)), 403);
	}

	@Test
	void anAdministratorGetsPastTheGuardEverywhere() throws Exception {
		// Without this, a guard that refused everyone would still pass the refusal tests above. The answer can be
		// 200 or 404 (the ids are random), or 503 without a torrent client, but never 401 or 403.
		String admin = TestTokens.admin(UUID.randomUUID());
		check(request -> request.cookie(new Cookie("homeflix_token", admin)),
				answered -> answered != 401 && answered != 403, "a refusal");
	}

	private void expect(Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder> caller, int status)
			throws Exception {
		check(caller, answered -> answered == status, "instead of " + status);
	}

	/** Sends every endpoint as {@code caller} makes it, and checks the status it answers. */
	private void check(Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder> caller,
			IntPredicate expected, String otherwise) throws Exception {
		List<String> problems = new ArrayList<>();
		for (String endpoint : ENDPOINTS) {
			String[] parts = endpoint.split(" ", 2);
			String path = parts[1].replaceAll("\\{[^}]+\\}", UUID.randomUUID().toString());
			MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(parts[0]), path);
			String body = BODIES.get(endpoint);
			if (body != null) {
				request.contentType(MediaType.APPLICATION_JSON).content(body);
			}
			int answered = mvc.perform(caller.apply(request)).andReturn().getResponse().getStatus();
			if (!expected.test(answered)) {
				problems.add(endpoint + " answered " + answered + ", " + otherwise);
			}
		}
		assertThat(problems).isEmpty();
	}

}
