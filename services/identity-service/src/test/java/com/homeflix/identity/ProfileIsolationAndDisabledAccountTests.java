package com.homeflix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.identity.session.SessionClaims;
import com.homeflix.identity.session.SessionCookies;
import com.homeflix.identity.session.TokenService;

import jakarta.servlet.http.Cookie;

/**
 * TST-02, phase M part (G11): disabled accounts (ACC-10, R-10) and profile isolation (PRF-04, R-06),
 * against a real PostgreSQL (ADR-0011). Sessions come from real sign-ins; the library service is
 * replaced by {@link FakeLibrary}.
 *
 * Complements {@code AccountManagementTests} (disable and re-enable through the administrator API)
 * and {@code ProfileTests} (a user changing a profile id in the URL): here, a live session with an
 * active profile, the administrator role, a profile id carried by a token, and probing.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfileIsolationAndDisabledAccountTests {

	private static final String ADMIN = "admin@homeflix.test";
	private static final String ALICE = "alice@homeflix.test";
	private static final String BOB = "bob@homeflix.test";
	private static final String PASSWORD = "correct-horse-battery";

	/** Hashed once: BCrypt strength 12 is slow on purpose. */
	private static String passwordHash;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TokenService tokens;

	private final FakeLibrary library = FakeLibrary.instance();

	private MockMvc mvc;
	private UUID aliceId;
	private UUID bobId;
	private UUID aliceKids;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		library.reset();
		if (passwordHash == null) {
			passwordHash = passwordEncoder.encode(PASSWORD);
		}
		jdbc.update("delete from account");
		insertAccount(ADMIN, true);
		aliceId = insertAccount(ALICE, false);
		bobId = insertAccount(BOB, false);
		aliceKids = jdbc.queryForObject("insert into profile (account_id, name) values (?, 'Kids') returning id",
				UUID.class, aliceId);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from account");
		library.reset();
	}

	// ---- Disabled accounts (ACC-10, R-10) ----

	@Test
	void disabledAccountCannotSignInRenewNorKeepUsingItsProfile() throws Exception {
		Cookie admin = signIn(ADMIN);
		Cookie alice = select(signIn(ALICE), aliceKids);

		mvc.perform(post("/accounts/" + aliceId + "/disable").cookie(admin)).andExpect(status().isOk());

		// No new session: same answer as a wrong password, so the account is not revealed.
		signInRequest(ALICE)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_credentials"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		// The live session, with its active profile, cannot be renewed or extended in any way.
		for (MockHttpServletRequestBuilder request : List.of(
				post("/auth/refresh"),
				get("/auth/me"),
				post("/profiles/" + aliceKids + "/select"))) {
			mvc.perform(request.cookie(alice))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error").value("unauthenticated"));
		}
	}

	@Test
	void reEnabledAccountSignsInAgainAndFindsItsProfiles() throws Exception {
		Cookie admin = signIn(ADMIN);
		mvc.perform(post("/accounts/" + aliceId + "/disable").cookie(admin)).andExpect(status().isOk());
		mvc.perform(post("/accounts/" + aliceId + "/enable").cookie(admin)).andExpect(status().isOk());

		Cookie alice = signIn(ALICE);
		mvc.perform(get("/profiles").cookie(alice))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(aliceKids.toString()));
	}

	@Test
	void deletedAccountCannotRenewItsSession() throws Exception {
		Cookie alice = select(signIn(ALICE), aliceKids);
		jdbc.update("delete from account where id = ?", aliceId);

		mvc.perform(post("/auth/refresh").cookie(alice))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
	}

	// ---- Profile isolation (PRF-04, R-06) ----

	@Test
	void administratorHasNoAccessToAnotherAccountsProfiles() throws Exception {
		// The administrator role manages accounts, not the profiles inside them.
		Cookie admin = signIn(ADMIN);

		for (MockHttpServletRequestBuilder request : List.of(
				put("/profiles/" + aliceKids).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"),
				delete("/profiles/" + aliceKids),
				post("/profiles/" + aliceKids + "/select"))) {
			mvc.perform(request.cookie(admin))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error").value("not_found"))
					.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		}
		mvc.perform(get("/profiles").cookie(admin)).andExpect(jsonPath("$.length()").value(0));

		assertThat(jdbc.queryForObject("select name from profile where id = ?", String.class, aliceKids))
				.isEqualTo("Kids");
		assertThat(library.calls()).as("nothing is deleted in the library").isEmpty();
	}

	@Test
	void anotherAccountsProfileCannotBeToldFromANonExistentOne() throws Exception {
		// Ids cannot be probed: same status and same body for "exists elsewhere" and "does not exist".
		Cookie bob = signIn(BOB);

		String elsewhere = mvc.perform(post("/profiles/" + aliceKids + "/select").cookie(bob))
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
		String nowhere = mvc.perform(post("/profiles/" + UUID.randomUUID() + "/select").cookie(bob))
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

		assertThat(elsewhere).isEqualTo(nowhere);
	}

	@Test
	void profileOfAnotherAccountInATokenIsNotHonoured() throws Exception {
		// A token cannot be forged, but the identity service still checks the profile it carries
		// belongs to the account before giving it back (defence in depth).
		Cookie bobWithAlicesProfile = new Cookie(SessionCookies.NAME,
				tokens.issue(new SessionClaims(bobId, false, aliceKids)));

		mvc.perform(get("/auth/me").cookie(bobWithAlicesProfile))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(bobId.toString()))
				.andExpect(jsonPath("$.profileId").isEmpty());

		MvcResult renewed = mvc.perform(post("/auth/refresh").cookie(bobWithAlicesProfile))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profileId").isEmpty())
				.andReturn();
		assertThat(tokens.verify(cookieFrom(renewed).getValue()).orElseThrow().profileId()).isNull();
	}

	@Test
	void selectingAnotherAccountsProfileLeavesTheSessionUnchanged() throws Exception {
		Cookie bob = signIn(BOB);

		mvc.perform(post("/profiles/" + aliceKids + "/select").cookie(bob))
				.andExpect(status().isNotFound())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		// Bob's own session still has no profile.
		mvc.perform(get("/auth/me").cookie(bob)).andExpect(jsonPath("$.profileId").isEmpty());
	}

	// ---- Helpers ----

	private ResultActions signInRequest(String email) throws Exception {
		return mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
	}

	private Cookie signIn(String email) throws Exception {
		return cookieFrom(signInRequest(email).andExpect(status().isOk()).andReturn());
	}

	private Cookie select(Cookie session, UUID profileId) throws Exception {
		return cookieFrom(mvc.perform(post("/profiles/" + profileId + "/select").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profileId").value(profileId.toString()))
				.andReturn());
	}

	private static Cookie cookieFrom(MvcResult result) {
		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		String prefix = SessionCookies.NAME + "=";
		assertThat(setCookie).startsWith(prefix);
		return new Cookie(SessionCookies.NAME, setCookie.substring(prefix.length(), setCookie.indexOf(';')));
	}

	private UUID insertAccount(String email, boolean admin) {
		return jdbc.queryForObject("insert into account (email, password_hash, is_admin) values (?, ?, ?) returning id",
				UUID.class, email, passwordHash, admin);
	}

}
