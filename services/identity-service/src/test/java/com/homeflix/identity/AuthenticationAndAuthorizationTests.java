package com.homeflix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.homeflix.identity.security.AccessGuard;
import com.homeflix.identity.security.Caller;
import com.homeflix.identity.session.SessionClaims;
import com.homeflix.identity.session.SessionCookies;
import com.homeflix.identity.session.SessionKeys;
import com.homeflix.identity.session.TestKeys;
import com.homeflix.identity.session.TokenService;
import com.homeflix.identity.web.ApiExceptionHandler;
import com.homeflix.identity.web.CrossSiteRequestFilter;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Authentication and authorization of the identity service (G7, TST-02, ACC-06, SEC-02), end to
 * end against a real PostgreSQL (ADR-0011): the session cookie always comes from a real sign-in,
 * never from a token built by the test, so the whole chain is exercised.
 *
 * <ul>
 * <li>successful and failed sign-in;</li>
 * <li>anonymous requests refused on every endpoint that is not anonymous in the contract, found
 * by scanning the application, so a new endpoint that forgets the guard fails this test;</li>
 * <li>a user refused on administrator operations. The identity service has no administrator
 * endpoint yet (account management comes in phase M), so a probe controller stands for them. It
 * uses the application's own {@link AccessGuard} bean, with cookies from a real sign-in.</li>
 * </ul>
 *
 * Disabled accounts and profile isolation need phase M features and are tested in G11.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthenticationAndAuthorizationTests {

	private static final String USER_EMAIL = "viewer@homeflix.test";
	private static final String ADMIN_EMAIL = "admin@homeflix.test";
	private static final String PASSWORD = "correct-horse-battery";

	/**
	 * Endpoints marked {@code x-permission: anonymous} in docs/api/identity.yaml, plus the
	 * service-to-service demo (issue #27). Every other endpoint must refuse anonymous requests.
	 * Adding an endpoint here is a security decision: check the contract and the permission matrix.
	 */
	private static final Set<String> ANONYMOUS_ENDPOINTS = Set.of(
			"GET /setup/status",
			"POST /setup/admin",
			"POST /auth/sign-in",
			"POST /auth/sign-out",
			"GET /ping");

	/** Hashed once: BCrypt strength 12 is slow on purpose. */
	private static String passwordHash;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private SessionKeys keys;

	@Autowired
	private AccessGuard guard;

	@Autowired
	private CrossSiteRequestFilter crossSiteRequestFilter;

	/** The application as deployed. MockMvc does not add servlet filters by itself. */
	private MockMvc app;

	/** Administrator and account operations, guarded by the application's own guard. */
	private MockMvc probe;
	private ProbeController probeController;

	private UUID userId;
	private UUID adminId;

	@BeforeEach
	void setUp() {
		app = MockMvcBuilders.webAppContextSetup(context).addFilters(crossSiteRequestFilter).build();
		probeController = new ProbeController(guard);
		probe = MockMvcBuilders.standaloneSetup(probeController)
				.setControllerAdvice(new ApiExceptionHandler())
				.addFilters(crossSiteRequestFilter)
				.build();

		if (passwordHash == null) {
			passwordHash = passwordEncoder.encode(PASSWORD);
		}
		jdbc.update("delete from account");
		userId = insertAccount(USER_EMAIL, false);
		adminId = insertAccount(ADMIN_EMAIL, true);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from account");
	}

	/** Stands for the administrator operations of phase M and the account operations. */
	@RestController
	static class ProbeController {

		private final AccessGuard guard;

		/** Counts the operations that actually ran, past the guard. */
		final AtomicInteger adminOperationsRun = new AtomicInteger();

		ProbeController(AccessGuard guard) {
			this.guard = guard;
		}

		@PostMapping("/test-probe/admin")
		Map<String, Object> adminOperation(HttpServletRequest request) {
			Caller caller = guard.requireAdmin(request);
			adminOperationsRun.incrementAndGet();
			return Map.of("accountId", caller.accountId().toString());
		}

		@GetMapping("/test-probe/account")
		Map<String, Object> accountOperation(HttpServletRequest request) {
			Caller caller = guard.requireAccount(request);
			return Map.of("accountId", caller.accountId().toString(), "admin", caller.admin());
		}

	}

	// ---- Successful sign-in ----

	@Test
	void userSignsInAndTheCookieOpensTheSession() throws Exception {
		String token = signIn(USER_EMAIL, PASSWORD);

		app.perform(get("/auth/me").cookie(session(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.email").value(USER_EMAIL))
				.andExpect(jsonPath("$.admin").value(false));
	}

	@Test
	void administratorSignsInWithTheAdministratorRole() throws Exception {
		String token = signIn(ADMIN_EMAIL, PASSWORD);

		app.perform(get("/auth/me").cookie(session(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(adminId.toString()))
				.andExpect(jsonPath("$.admin").value(true));
	}

	@Test
	void renewedCookieKeepsTheSessionOpen() throws Exception {
		String first = signIn(USER_EMAIL, PASSWORD);

		String renewed = tokenFrom(app.perform(post("/auth/refresh").cookie(session(first)))
				.andExpect(status().isOk())
				.andReturn());

		app.perform(get("/auth/me").cookie(session(renewed)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId.toString()));
	}

	// ---- Failed sign-in ----

	@Test
	void failedSignInsOpenNoSession() throws Exception {
		Map<String, String> wrongCredentials = new LinkedHashMap<>();
		wrongCredentials.put("wrong password", credentials(USER_EMAIL, "wrong-password-123"));
		wrongCredentials.put("password in another case", credentials(USER_EMAIL, PASSWORD.toUpperCase()));
		wrongCredentials.put("password with a trailing space", credentials(USER_EMAIL, PASSWORD + " "));
		wrongCredentials.put("password of another account", credentials(ADMIN_EMAIL, "wrong-password-123"));
		wrongCredentials.put("unknown email", credentials("nobody@homeflix.test", PASSWORD));
		wrongCredentials.put("SQL in the password", credentials(USER_EMAIL, "' OR '1'='1"));

		for (Map.Entry<String, String> attempt : wrongCredentials.entrySet()) {
			MockHttpServletResponse response = postJson("/auth/sign-in", attempt.getValue())
					.andExpect(status().isUnauthorized())
					// Same answer whatever went wrong, so accounts are not revealed (ACC-10).
					.andExpect(jsonPath("$.error").value("invalid_credentials"))
					.andExpect(jsonPath("$.message").value("The email or password is incorrect."))
					.andReturn().getResponse();
			assertThat(opensASession(response)).as(attempt.getKey()).isFalse();
		}
	}

	@Test
	void incompleteSignInsAreRejectedWithoutASession() throws Exception {
		Map<String, String> incomplete = new LinkedHashMap<>();
		incomplete.put("empty password", credentials(USER_EMAIL, ""));
		incomplete.put("no password", "{\"email\":\"" + USER_EMAIL + "\"}");
		incomplete.put("no email", "{\"password\":\"" + PASSWORD + "\"}");
		incomplete.put("empty body", "{}");

		for (Map.Entry<String, String> attempt : incomplete.entrySet()) {
			MockHttpServletResponse response = postJson("/auth/sign-in", attempt.getValue())
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("validation_failed"))
					.andReturn().getResponse();
			assertThat(opensASession(response)).as(attempt.getKey()).isFalse();
		}
	}

	// ---- Anonymous access ----

	@Test
	void everyEndpointOutsideTheAnonymousListRefusesAnonymousRequests() throws Exception {
		List<Endpoint> endpoints = applicationEndpoints();
		List<String> found = endpoints.stream().map(Endpoint::key).toList();

		// The scan really finds the endpoints, and the list above has no stale entry.
		assertThat(found).containsAll(ANONYMOUS_ENDPOINTS).contains("GET /auth/me", "POST /auth/refresh");

		List<String> problems = new ArrayList<>();
		for (Endpoint endpoint : endpoints) {
			if (ANONYMOUS_ENDPOINTS.contains(endpoint.key())) {
				continue;
			}
			String path = endpoint.path().replaceAll("\\{[^}]+\\}", UUID.randomUUID().toString());
			MockHttpServletRequestBuilder anonymous = request(endpoint.method(), path);
			if (endpoint.readsBody()) {
				anonymous.contentType(MediaType.APPLICATION_JSON).content("{}");
			}
			MockHttpServletResponse response = app.perform(anonymous).andReturn().getResponse();

			// An endpoint with a body validates it before its method, and so before the guard, runs:
			// a 400 is a refusal too. Anything else must be 401.
			int status = response.getStatus();
			if (status != 401 && !(endpoint.readsBody() && status == 400)) {
				problems.add(endpoint.key() + " answered " + status + " instead of 401");
			}
			if (opensASession(response)) {
				problems.add(endpoint.key() + " set a session cookie");
			}
		}
		assertThat(problems)
				.as("Endpoints reachable without signing in. Call the AccessGuard first, "
						+ "or add the endpoint to ANONYMOUS_ENDPOINTS if the contract says anonymous.")
				.isEmpty();
	}

	@Test
	void invalidOrForgedSessionsCountAsAnonymous() throws Exception {
		Map<String, MockHttpServletRequestBuilder[]> attempts = new LinkedHashMap<>();
		for (Map.Entry<String, String> forged : forgedTokens(signIn(USER_EMAIL, PASSWORD)).entrySet()) {
			attempts.put(forged.getKey(), new MockHttpServletRequestBuilder[] {
					get("/auth/me").cookie(session(forged.getValue())),
					post("/auth/refresh").cookie(session(forged.getValue())) });
		}
		String valid = signIn(USER_EMAIL, PASSWORD);
		attempts.put("valid token under another cookie name", new MockHttpServletRequestBuilder[] {
				get("/auth/me").cookie(new Cookie("token", valid)),
				post("/auth/refresh").cookie(new Cookie("token", valid)) });
		// R-06: only the cookie counts, never a header.
		attempts.put("valid token in the Authorization header", new MockHttpServletRequestBuilder[] {
				get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + valid),
				post("/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + valid) });

		for (Map.Entry<String, MockHttpServletRequestBuilder[]> attempt : attempts.entrySet()) {
			for (MockHttpServletRequestBuilder request : attempt.getValue()) {
				MockHttpServletResponse response = app.perform(request).andReturn().getResponse();
				assertThat(response.getStatus()).as(attempt.getKey()).isEqualTo(401);
				assertThat(response.getContentAsString()).as(attempt.getKey()).contains("\"unauthenticated\"");
				assertThat(opensASession(response)).as(attempt.getKey()).isFalse();
			}
		}
	}

	// ---- Administrator permissions ----

	@Test
	void anonymousCannotCallAnAdministratorOperation() throws Exception {
		probe.perform(post("/test-probe/admin"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));

		assertThat(probeController.adminOperationsRun).hasValue(0);
	}

	@Test
	void signedInUserCannotCallAnAdministratorOperation() throws Exception {
		String token = signIn(USER_EMAIL, PASSWORD);

		probe.perform(post("/test-probe/admin").cookie(session(token)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"))
				.andExpect(jsonPath("$.message").value("You do not have permission to do this."));
		// The same session is fine for account operations: it is the role that is refused.
		probe.perform(get("/test-probe/account").cookie(session(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(userId.toString()))
				.andExpect(jsonPath("$.admin").value(false));

		assertThat(probeController.adminOperationsRun).hasValue(0);
	}

	@Test
	void signedInAdministratorCanCallAnAdministratorOperation() throws Exception {
		String token = signIn(ADMIN_EMAIL, PASSWORD);

		probe.perform(post("/test-probe/admin").cookie(session(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(adminId.toString()));

		assertThat(probeController.adminOperationsRun).hasValue(1);
	}

	@Test
	void userCannotClaimTheAdministratorRole() throws Exception {
		String userToken = signIn(USER_EMAIL, PASSWORD);

		// A token edited or re-signed to say admin=true is not a session at all.
		for (Map.Entry<String, String> forged : forgedTokens(userToken).entrySet()) {
			MockHttpServletResponse response = probe.perform(post("/test-probe/admin").cookie(session(forged.getValue())))
					.andReturn().getResponse();
			assertThat(response.getStatus()).as(forged.getKey()).isEqualTo(401);
		}

		// A real user session with the role claimed elsewhere in the request is still a user (R-06).
		probe.perform(post("/test-probe/admin")
						.cookie(session(userToken))
						.header("X-Admin", "true")
						.header("X-Account-Id", adminId.toString())
						.param("admin", "true")
						.param("accountId", adminId.toString()))
				.andExpect(status().isForbidden());

		assertThat(probeController.adminOperationsRun).hasValue(0);
	}

	@Test
	void promotionTakesEffectAtTheNextRenewal() throws Exception {
		String before = signIn(USER_EMAIL, PASSWORD);
		jdbc.update("update account set is_admin = true where id = ?", userId);

		// The role is in the signed token, so the current token stays a user token (R-10).
		probe.perform(post("/test-probe/admin").cookie(session(before))).andExpect(status().isForbidden());

		String renewed = tokenFrom(app.perform(post("/auth/refresh").cookie(session(before)))
				.andExpect(status().isOk())
				.andReturn());
		probe.perform(post("/test-probe/admin").cookie(session(renewed))).andExpect(status().isOk());
	}

	@Test
	void demotionTakesEffectAtTheNextRenewal() throws Exception {
		String before = signIn(ADMIN_EMAIL, PASSWORD);
		probe.perform(post("/test-probe/admin").cookie(session(before))).andExpect(status().isOk());
		jdbc.update("update account set is_admin = false where id = ?", adminId);

		// Accepted by design (ADR-0009, R-10): the old token keeps its role until it expires,
		// at most 15 minutes, because there is no revocation list.
		probe.perform(post("/test-probe/admin").cookie(session(before))).andExpect(status().isOk());

		String renewed = tokenFrom(app.perform(post("/auth/refresh").cookie(session(before)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.admin").value(false))
				.andReturn());
		probe.perform(post("/test-probe/admin").cookie(session(renewed)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
	}

	@Test
	void crossSiteAdministratorRequestIsRefused() throws Exception {
		// R-09: another site cannot use the administrator's cookie to act for them.
		String token = signIn(ADMIN_EMAIL, PASSWORD);

		probe.perform(post("/test-probe/admin").cookie(session(token)).header("Sec-Fetch-Site", "cross-site"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));

		assertThat(probeController.adminOperationsRun).hasValue(0);
	}

	// ---- Helpers ----

	/** One HTTP method and path of the application, as mapped by Spring MVC. */
	private record Endpoint(HttpMethod method, String path, boolean readsBody) {

		String key() {
			return method.name() + " " + path;
		}

	}

	/** The endpoints of HomeFlix code. Framework ones such as /error are left out. */
	private List<Endpoint> applicationEndpoints() {
		List<Endpoint> endpoints = new ArrayList<>();
		mappings.getHandlerMethods().forEach((info, handler) -> {
			if (!handler.getBeanType().getPackageName().startsWith("com.homeflix")) {
				return;
			}
			boolean readsBody = Arrays.stream(handler.getMethodParameters())
					.anyMatch(parameter -> parameter.hasParameterAnnotation(RequestBody.class));
			Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
			// A mapping without a method accepts them all: GET stands for them.
			Set<RequestMethod> tried = methods.isEmpty() ? Set.of(RequestMethod.GET) : methods;
			for (String path : info.getPatternValues()) {
				for (RequestMethod method : tried) {
					endpoints.add(new Endpoint(HttpMethod.valueOf(method.name()), path, readsBody));
				}
			}
		});
		return endpoints;
	}

	/**
	 * Tokens that must not be accepted, all derived from a real user token. The ones that try to
	 * become administrator are the dangerous ones: a guard that only decoded the payload would let
	 * them through.
	 */
	private Map<String, String> forgedTokens(String userToken) throws Exception {
		String[] parts = userToken.split("\\.");
		String header = parts[0];
		String payload = decode(parts[1]);
		String signature = parts[2];

		String adminPayload = payload.replace("\"admin\":false", "\"admin\":true");
		assertThat(adminPayload).as("the user token carries admin=false").isNotEqualTo(payload);

		Map<String, String> forged = new LinkedHashMap<>();
		forged.put("empty token", "");
		forged.put("not a token", "not-a-token");
		forged.put("token cut short", userToken.substring(0, userToken.length() - 10));
		forged.put("payload changed to admin, original signature",
				header + "." + encode(adminPayload) + "." + signature);
		forged.put("unsigned token (alg none)",
				encode("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + encode(adminPayload) + ".");

		// Algorithm confusion: HS256 with the public key as the shared secret.
		SignedJWT hmac = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.parse(adminPayload));
		hmac.sign(new MACSigner(keys.publicKey().getEncoded()));
		forged.put("HS256 signed with the public key", hmac.serialize());

		TokenService otherKey = new TokenService(SessionKeys.fromKeyText(TestKeys.newPrivateKey(2048)),
				Clock.systemUTC(), Duration.ofMinutes(15));
		forged.put("signed with another key", otherKey.issue(new SessionClaims(userId, true, null)));

		TokenService twentyMinutesAgo = new TokenService(keys,
				Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-20)), Duration.ofMinutes(15));
		forged.put("expired", twentyMinutesAgo.issue(new SessionClaims(userId, false, null)));
		return forged;
	}

	/** Signs in through the real endpoint and returns the token of the cookie it sets. */
	private String signIn(String email, String password) throws Exception {
		return tokenFrom(postJson("/auth/sign-in", credentials(email, password))
				.andExpect(status().isOk())
				.andExpect(header().exists(HttpHeaders.SET_COOKIE))
				.andReturn());
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return app.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private UUID insertAccount(String email, boolean admin) {
		return jdbc.queryForObject(
				"insert into account (email, password_hash, is_admin) values (?, ?, ?) returning id",
				UUID.class, email, passwordHash, admin);
	}

	/** True when the response sets a non-empty session cookie (a cleared one does not count). */
	private static boolean opensASession(MockHttpServletResponse response) {
		return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
				.anyMatch(cookie -> cookie.startsWith(SessionCookies.NAME + "=")
						&& !cookie.startsWith(SessionCookies.NAME + "=;")
						&& !cookie.contains("Max-Age=0"));
	}

	private static String credentials(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static Cookie session(String token) {
		return new Cookie(SessionCookies.NAME, token);
	}

	private static String tokenFrom(MvcResult result) {
		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		String prefix = SessionCookies.NAME + "=";
		assertThat(setCookie).startsWith(prefix);
		return setCookie.substring(prefix.length(), setCookie.indexOf(';'));
	}

	private static String decode(String base64Url) {
		return new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
	}

	private static String encode(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
