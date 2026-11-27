package com.homeflix.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntPredicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.homeflix.library.security.TestTokens;

import jakarta.servlet.http.Cookie;

/**
 * Every endpoint of the library service refuses the callers its contract does not allow, with real signed
 * session tokens and the real service secrets (K4; SEC-02, SEC-10, ACC-06, PLY-04; R-06, R-12). The endpoints
 * are found by scanning the application, so a new endpoint that is missing from {@link #PERMISSIONS}, or a
 * guard that is forgotten, fails this test.
 *
 * <p>Each request carries a valid body, so a refusal comes from the guard and not from validation. The
 * permissions are those of {@code x-permission} in {@code docs/api/library.yaml}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EndpointPermissionTests {

	@DynamicPropertySource
	static void identityPublicKey(DynamicPropertyRegistry registry) {
		registry.add("homeflix.security.public-key-file",
				() -> TestTokens.writeTemporaryPem(TestTokens.toPem(TestTokens.identityPublicKey())));
	}

	/** Same values as {@code src/test/resources/application.properties}. */
	private static final String ACQUISITION_SECRET = "test-acquisition-token-not-a-secret";
	private static final String IDENTITY_SECRET = "test-identity-token-not-a-secret";

	private enum Permission { ANONYMOUS, ACCOUNT, ADMIN, PROFILE, ACQUISITION_SERVICE, IDENTITY_SERVICE }

	/** The contract's permission of every endpoint. */
	private static final Map<String, Permission> PERMISSIONS = new LinkedHashMap<>();
	static {
		PERMISSIONS.put("GET /roots", Permission.ADMIN);
		PERMISSIONS.put("POST /roots", Permission.ADMIN);
		PERMISSIONS.put("DELETE /roots/{rootId}", Permission.ADMIN);
		PERMISSIONS.put("POST /scans", Permission.ADMIN);
		PERMISSIONS.put("GET /media", Permission.ACCOUNT);
		PERMISSIONS.put("GET /media/{mediaId}", Permission.ACCOUNT);
		PERMISSIONS.put("PUT /media/{mediaId}", Permission.ADMIN);
		PERMISSIONS.put("DELETE /media/{mediaId}", Permission.ADMIN);
		PERMISSIONS.put("GET /categories", Permission.ACCOUNT);
		PERMISSIONS.put("GET /genres", Permission.ACCOUNT);
		PERMISSIONS.put("GET /media/{mediaId}/stream", Permission.ACCOUNT);
		PERMISSIONS.put("GET /favourites", Permission.PROFILE);
		PERMISSIONS.put("PUT /favourites/{mediaId}", Permission.PROFILE);
		PERMISSIONS.put("DELETE /favourites/{mediaId}", Permission.PROFILE);
		PERMISSIONS.put("GET /progress", Permission.PROFILE);
		PERMISSIONS.put("GET /progress/{mediaId}", Permission.PROFILE);
		PERMISSIONS.put("PUT /progress/{mediaId}", Permission.PROFILE);
		PERMISSIONS.put("DELETE /progress/{mediaId}", Permission.PROFILE);
		PERMISSIONS.put("PUT /internal/downloads/{entryId}", Permission.ACQUISITION_SERVICE);
		PERMISSIONS.put("POST /internal/downloads/{entryId}/completed", Permission.ACQUISITION_SERVICE);
		PERMISSIONS.put("POST /internal/downloads/{entryId}/failed", Permission.ACQUISITION_SERVICE);
		PERMISSIONS.put("DELETE /internal/profiles/{profileId}/data", Permission.IDENTITY_SERVICE);
		// The service-to-service demo (D5), not in the contract, anonymous on purpose: see
		// docs/architecture/service-to-service-demo.md.
		PERMISSIONS.put("GET /demo/identity", Permission.ANONYMOUS);
	}

	/** The title sent in bodies, so what an allowed call creates can be removed. */
	private static final String TEST_TITLE = "Endpoint permission test";

	/** A valid body for the endpoints that read one. */
	private static final Map<String, String> BODIES = Map.of(
			"POST /roots", "{\"path\":\"/media/library\"}",
			"PUT /media/{mediaId}", "{\"title\":\"" + TEST_TITLE + "\"}",
			"PUT /progress/{mediaId}", "{\"positionSeconds\":10,\"durationSeconds\":100}",
			"PUT /internal/downloads/{entryId}", "{\"title\":\"" + TEST_TITLE + "\"}",
			"POST /internal/downloads/{entryId}/completed", "{\"relativePath\":\"film.mp4\"}");

	private final UUID account = UUID.randomUUID();
	private final UUID profile = UUID.randomUUID();

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void everyEndpointHasAPermissionAndEveryPermissionAnEndpoint() {
		assertThat(new TreeSet<>(ApplicationEndpoints.of(mappings))).isEqualTo(new TreeSet<>(PERMISSIONS.keySet()));
	}

	@Test
	void anonymousCallersAreRefusedEverywhereButTheDemo() throws Exception {
		expect(Set.of(Permission.ACCOUNT, Permission.ADMIN, Permission.PROFILE, Permission.ACQUISITION_SERVICE,
				Permission.IDENTITY_SERVICE), request -> request, 401);
	}

	@Test
	void aSignedInUserIsRefusedOnAdministratorOperations() throws Exception {
		String user = TestTokens.user(account, profile);
		expect(Set.of(Permission.ADMIN), request -> request.cookie(session(user)), 403);
	}

	@Test
	void anAccountWithoutAProfileIsRefusedOnProfileData() throws Exception {
		// An administrator has no profile in its token either: no extra access to profile data.
		for (String token : List.of(TestTokens.admin(account), TestTokens.token(TestTokens.IDENTITY_KEYS,
				"homeflix-identity", account, false, null, Instant.now(), Duration.ofMinutes(15)))) {
			expect(Set.of(Permission.PROFILE), request -> request.cookie(session(token)), 403);
		}
	}

	@Test
	void internalEndpointsRefuseSessionsAndTheOtherServicesSecret() throws Exception {
		// A user or an administrator session is never a service identity.
		for (String token : List.of(TestTokens.user(account, profile), TestTokens.admin(account))) {
			expect(Set.of(Permission.ACQUISITION_SERVICE, Permission.IDENTITY_SERVICE),
					request -> request.cookie(session(token)), 401);
		}
		// Each secret opens only its own caller's endpoints (ADR-0022).
		expect(Set.of(Permission.ACQUISITION_SERVICE), request -> request.header("X-Service-Token", IDENTITY_SECRET),
				401);
		expect(Set.of(Permission.IDENTITY_SERVICE), request -> request.header("X-Service-Token", ACQUISITION_SECRET),
				401);
	}

	@Test
	void theAllowedCallerGetsPastTheGuardEverywhere() throws Exception {
		// Without this, a guard that refused everyone would still pass the refusal tests above. The answer can be
		// 200, 204 or 404 (the ids are random), but never 401 or 403.
		String user = TestTokens.user(account, profile);
		Map<Permission, Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder>> allowed = Map.of(
				Permission.ANONYMOUS, request -> request,
				Permission.ACCOUNT, request -> request.cookie(session(user)),
				Permission.ADMIN, request -> request.cookie(session(TestTokens.admin(account))),
				Permission.PROFILE, request -> request.cookie(session(user)),
				Permission.ACQUISITION_SERVICE, request -> request.header("X-Service-Token", ACQUISITION_SECRET),
				Permission.IDENTITY_SERVICE, request -> request.header("X-Service-Token", IDENTITY_SECRET));
		assertThat(allowed.keySet()).containsExactlyInAnyOrder(Permission.values());
		for (Map.Entry<Permission, Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder>> caller
				: allowed.entrySet()) {
			check(Set.of(caller.getKey()), caller.getValue(), answered -> answered != 401 && answered != 403,
					"a refusal");
		}
	}

	@AfterEach
	void removeWhatTheAllowedCallsCreated() {
		// PUT /internal/downloads/{entryId} lists a downloading title.
		jdbc.update("delete from media_item where title = ?", TEST_TITLE);
	}

	/** Sends each endpoint of these permissions, as {@code caller} makes it, and expects {@code status}. */
	private void expect(Set<Permission> permissions,
			Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder> caller, int status) throws Exception {
		check(permissions, caller, answered -> answered == status, "instead of " + status);
	}

	/** Sends each endpoint of these permissions, as {@code caller} makes it, and checks the status it answers. */
	private void check(Set<Permission> permissions,
			Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder> caller, IntPredicate expected,
			String otherwise) throws Exception {
		List<String> problems = new ArrayList<>();
		for (Map.Entry<String, Permission> endpoint : PERMISSIONS.entrySet()) {
			if (!permissions.contains(endpoint.getValue())) {
				continue;
			}
			String[] parts = endpoint.getKey().split(" ", 2);
			String path = parts[1].replaceAll("\\{[^}]+\\}", UUID.randomUUID().toString());
			MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(parts[0]), path);
			String body = BODIES.get(endpoint.getKey());
			if (body != null) {
				request.contentType(MediaType.APPLICATION_JSON).content(body);
			}
			int answered = mvc.perform(caller.apply(request)).andReturn().getResponse().getStatus();
			if (!expected.test(answered)) {
				problems.add(endpoint.getKey() + " answered " + answered + ", " + otherwise);
			}
		}
		assertThat(problems).isEmpty();
	}

	private static Cookie session(String token) {
		return new Cookie("homeflix_token", token);
	}

}
