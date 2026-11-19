package com.homeflix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.identity.session.SessionClaims;
import com.homeflix.identity.session.SessionCookies;
import com.homeflix.identity.session.SessionKeys;
import com.homeflix.identity.session.TestKeys;
import com.homeflix.identity.session.TokenService;
import com.homeflix.identity.web.CrossSiteRequestFilter;

import jakarta.servlet.http.Cookie;

/**
 * Sign-in, sign-out, renewal and current account (ACC-03, ACC-10, R-09, R-10, TST-02),
 * against a real PostgreSQL (ADR-0011).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SessionTests {

	private static final String EMAIL = "viewer@homeflix.test";
	private static final String PASSWORD = "correct-horse-battery";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private SessionKeys keys;

	@Autowired
	private TokenService tokens;

	@Autowired
	private CrossSiteRequestFilter crossSiteRequestFilter;

	private MockMvc mvc;
	private UUID accountId;

	@BeforeEach
	void setUp() {
		// MockMvc does not add servlet filters by itself.
		mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(crossSiteRequestFilter).build();
		jdbc.update("delete from account");
		accountId = jdbc.queryForObject(
				"insert into account (email, password_hash) values (?, ?) returning id",
				UUID.class, EMAIL, passwordEncoder.encode(PASSWORD));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from account");
	}

	// ---- Sign-in ----

	@Test
	void validSignInSetsTheSessionCookie() throws Exception {
		MvcResult result = signIn(EMAIL, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(accountId.toString()))
				.andExpect(jsonPath("$.email").value(EMAIL))
				.andExpect(jsonPath("$.admin").value(false))
				.andExpect(jsonPath("$.profileId").value(nullValue()))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn();

		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie)
				.startsWith(SessionCookies.NAME + "=")
				.contains("Path=/", "HttpOnly", "SameSite=Strict", "Max-Age=900");

		SessionClaims claims = tokens.verify(tokenFrom(result)).orElseThrow();
		assertThat(claims).isEqualTo(new SessionClaims(accountId, false, null));
	}

	@Test
	void administratorFlagIsInTheToken() throws Exception {
		jdbc.update("update account set is_admin = true where id = ?", accountId);

		MvcResult result = signIn(EMAIL, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.admin").value(true))
				.andReturn();

		assertThat(tokens.verify(tokenFrom(result)).orElseThrow().admin()).isTrue();
	}

	@Test
	void emailIsCaseInsensitive() throws Exception {
		signIn("  Viewer@HomeFlix.TEST ", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		signIn(EMAIL, "wrong-password-123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_credentials"))
				.andExpect(jsonPath("$.message").value("The email or password is incorrect."))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void unknownEmailGetsTheSameAnswerAsAWrongPassword() throws Exception {
		signIn("nobody@homeflix.test", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_credentials"))
				.andExpect(jsonPath("$.message").value("The email or password is incorrect."))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void disabledAccountCannotSignIn() throws Exception {
		jdbc.update("update account set disabled = true where id = ?", accountId);

		signIn(EMAIL, PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_credentials"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void passwordOverTheBcryptLimitIsRejectedAsInvalidCredentials() throws Exception {
		// 40 characters but 80 bytes.
		signIn(EMAIL, "é".repeat(40))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_credentials"));
	}

	@Test
	void missingFieldsAreRejected() throws Exception {
		postJson("/auth/sign-in", "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields.email").exists())
				.andExpect(jsonPath("$.fields.password").exists());
	}

	// ---- Sign-out ----

	@Test
	void signOutClearsTheCookie() throws Exception {
		mvc.perform(post("/auth/sign-out").cookie(sessionCookie(validToken())))
				.andExpect(status().isNoContent())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(SessionCookies.NAME + "=;")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
	}

	@Test
	void signOutWithoutSessionStillSucceeds() throws Exception {
		mvc.perform(post("/auth/sign-out"))
				.andExpect(status().isNoContent())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
	}

	// ---- Renewal (R-10) ----

	@Test
	void validTokenIsRenewed() throws Exception {
		MvcResult result = mvc.perform(post("/auth/refresh").cookie(sessionCookie(validToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(accountId.toString()))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=900")))
				.andReturn();

		assertThat(tokens.verify(tokenFrom(result))).isPresent();
	}

	@Test
	void renewalKeepsTheActiveProfile() throws Exception {
		UUID profileId = jdbc.queryForObject(
				"insert into profile (account_id, name) values (?, 'Kids') returning id", UUID.class, accountId);
		String token = tokens.issue(new SessionClaims(accountId, false, profileId));

		MvcResult result = mvc.perform(post("/auth/refresh").cookie(sessionCookie(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profileId").value(profileId.toString()))
				.andReturn();

		assertThat(tokens.verify(tokenFrom(result)).orElseThrow().profileId()).isEqualTo(profileId);
	}

	@Test
	void renewalIsRefusedForADisabledAccount() throws Exception {
		String token = validToken();
		jdbc.update("update account set disabled = true where id = ?", accountId);

		expectUnauthenticated(mvc.perform(post("/auth/refresh").cookie(sessionCookie(token))));
	}

	@Test
	void renewalIsRefusedForADeletedAccount() throws Exception {
		String token = validToken();
		jdbc.update("delete from account where id = ?", accountId);

		expectUnauthenticated(mvc.perform(post("/auth/refresh").cookie(sessionCookie(token))));
	}

	@Test
	void renewalIsRefusedForAnExpiredToken() throws Exception {
		TokenService twentyMinutesAgo = new TokenService(keys,
				Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-20)), Duration.ofMinutes(15));
		String expired = twentyMinutesAgo.issue(new SessionClaims(accountId, false, null));

		expectUnauthenticated(mvc.perform(post("/auth/refresh").cookie(sessionCookie(expired))));
	}

	@Test
	void renewalIsRefusedForATokenSignedWithAnotherKey() throws Exception {
		TokenService attacker = new TokenService(SessionKeys.fromKeyText(TestKeys.newPrivateKey(2048)),
				Clock.systemUTC(), Duration.ofMinutes(15));
		String forged = attacker.issue(new SessionClaims(accountId, true, null));

		expectUnauthenticated(mvc.perform(post("/auth/refresh").cookie(sessionCookie(forged))));
	}

	@Test
	void renewalWithoutCookieIsRefused() throws Exception {
		expectUnauthenticated(mvc.perform(post("/auth/refresh")));
	}

	@Test
	void renewalTakesTheCurrentAdministratorFlag() throws Exception {
		String token = validToken();
		jdbc.update("update account set is_admin = true where id = ?", accountId);

		MvcResult result = mvc.perform(post("/auth/refresh").cookie(sessionCookie(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.admin").value(true))
				.andReturn();

		assertThat(tokens.verify(tokenFrom(result)).orElseThrow().admin()).isTrue();
	}

	// ---- Current account ----

	@Test
	void currentAccountIsReturned() throws Exception {
		mvc.perform(get("/auth/me").cookie(sessionCookie(validToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(accountId.toString()))
				.andExpect(jsonPath("$.email").value(EMAIL))
				.andExpect(jsonPath("$.admin").value(false));
	}

	@Test
	void currentAccountWithoutSessionIsRefused() throws Exception {
		expectUnauthenticated(mvc.perform(get("/auth/me")));
	}

	// ---- CSRF (R-09) ----

	@Test
	void crossSiteStateChangingRequestIsRefused() throws Exception {
		mvc.perform(post("/auth/sign-in")
						.header("Sec-Fetch-Site", "cross-site")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials(EMAIL, PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void sameOriginRequestIsAccepted() throws Exception {
		mvc.perform(post("/auth/sign-in")
						.header("Sec-Fetch-Site", "same-origin")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials(EMAIL, PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	void formEncodedSignInIsRefused() throws Exception {
		// An HTML form on another site cannot send JSON, so it cannot reach sign-in.
		mvc.perform(post("/auth/sign-in")
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.content("email=" + EMAIL + "&password=" + PASSWORD))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	// ---- Helpers ----

	private ResultActions signIn(String email, String password) throws Exception {
		return postJson("/auth/sign-in", credentials(email, password));
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String credentials(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private String validToken() {
		return tokens.issue(new SessionClaims(accountId, false, null));
	}

	private static Cookie sessionCookie(String token) {
		return new Cookie(SessionCookies.NAME, token);
	}

	private static String tokenFrom(MvcResult result) {
		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		String prefix = SessionCookies.NAME + "=";
		return setCookie.substring(prefix.length(), setCookie.indexOf(';'));
	}

	private static void expectUnauthenticated(ResultActions actions) throws Exception {
		actions.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"))
				.andExpect(jsonPath("$.message").value("Please sign in."))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
	}

}
