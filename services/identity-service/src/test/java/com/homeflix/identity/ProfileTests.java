package com.homeflix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import com.homeflix.identity.profile.ProfileLimitReachedException;
import com.homeflix.identity.profile.ProfileRequest;
import com.homeflix.identity.profile.ProfileService;
import com.homeflix.identity.security.Caller;
import com.homeflix.identity.session.SessionClaims;
import com.homeflix.identity.session.SessionCookies;
import com.homeflix.identity.session.TokenService;
import com.homeflix.identity.web.CrossSiteRequestFilter;

import jakarta.servlet.http.Cookie;

/**
 * Viewing profiles and active-profile selection (G9: PRF-01, PRF-02, PRF-04, R-06), against a
 * real PostgreSQL (ADR-0011). Sessions come from a real sign-in. The library service is replaced by
 * {@link FakeLibrary}. Not transactional: the concurrency test needs committed data.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfileTests {

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

	@Autowired
	private ProfileService profileService;

	@Autowired
	private CrossSiteRequestFilter crossSiteRequestFilter;

	private final FakeLibrary library = FakeLibrary.instance();

	private MockMvc mvc;
	private UUID aliceId;
	private UUID bobId;
	private Cookie alice;
	private Cookie bob;

	@BeforeEach
	void setUp() throws Exception {
		mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(crossSiteRequestFilter).build();
		library.reset();
		if (passwordHash == null) {
			passwordHash = passwordEncoder.encode(PASSWORD);
		}
		jdbc.update("delete from account");
		aliceId = insertAccount(ALICE);
		bobId = insertAccount(BOB);
		alice = signIn(ALICE);
		bob = signIn(BOB);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from account");
		library.reset();
	}

	// ---- Create and list (PRF-01) ----

	@Test
	void accountCreatesAndListsItsProfiles() throws Exception {
		createProfile(alice, "  Kids  ")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Kids"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
		createProfile(alice, "Parents").andExpect(status().isCreated());

		mvc.perform(get("/profiles").cookie(alice))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].name").value("Kids"))
				.andExpect(jsonPath("$[1].name").value("Parents"));
	}

	@Test
	void invalidNamesAreRejected() throws Exception {
		for (String name : new String[] { "", "   ", "x".repeat(31) }) {
			createProfile(alice, name)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("validation_failed"))
					.andExpect(jsonPath("$.fields.name").exists());
		}
		createProfile(alice, "x".repeat(30)).andExpect(status().isCreated());
		assertThat(profileCount(aliceId)).isEqualTo(1);
	}

	@Test
	void sixthProfileIsRefused() throws Exception {
		for (int i = 1; i <= 5; i++) {
			createProfile(alice, "Profile " + i).andExpect(status().isCreated());
		}
		createProfile(alice, "Profile 6")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("profile_limit_reached"))
				.andExpect(jsonPath("$.message").value("This account already has the maximum number of profiles."));

		// The limit is per account.
		createProfile(bob, "Profile 6").andExpect(status().isCreated());
	}

	@Test
	void simultaneousCreationsStayWithinTheLimit() throws Exception {
		for (int i = 1; i <= 4; i++) {
			insertProfile(aliceId, "Profile " + i);
		}
		Caller caller = new Caller(aliceId, false, null);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (String name : List.of("Fifth A", "Fifth B")) {
				results.add(pool.submit(() -> {
					start.await();
					try {
						profileService.create(caller, new ProfileRequest(name));
						return true;
					}
					catch (ProfileLimitReachedException ex) {
						return false;
					}
				}));
			}
			start.countDown();
			int created = 0;
			for (Future<Boolean> result : results) {
				if (result.get(30, TimeUnit.SECONDS)) {
					created++;
				}
			}
			assertThat(created).isEqualTo(1);
			assertThat(profileCount(aliceId)).isEqualTo(5);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void namesAreUniqueWithinAnAccountOnly() throws Exception {
		createProfile(alice, "Kids").andExpect(status().isCreated());

		createProfile(alice, "Kids")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("profile_name_taken"))
				.andExpect(jsonPath("$.message").value("A profile with this name already exists."));
		createProfile(alice, " Kids ").andExpect(status().isConflict());
		// Case-sensitive, as in the data model.
		createProfile(alice, "kids").andExpect(status().isCreated());
		// Another account may use the same name.
		createProfile(bob, "Kids").andExpect(status().isCreated());
	}

	// ---- Rename ----

	@Test
	void accountRenamesItsProfile() throws Exception {
		UUID kids = insertProfile(aliceId, "Kids");
		insertProfile(aliceId, "Parents");

		rename(alice, kids, " Children ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(kids.toString()))
				.andExpect(jsonPath("$.name").value("Children"));
		assertThat(profileName(kids)).isEqualTo("Children");

		// Same name again: nothing changes.
		rename(alice, kids, "Children").andExpect(status().isOk());
		rename(alice, kids, "Parents")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("profile_name_taken"));
		rename(alice, kids, "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.name").exists());
		assertThat(profileName(kids)).isEqualTo("Children");
	}

	// ---- Another account's profile (PRF-04, R-06, AS-05.5) ----

	@Test
	void anotherAccountsProfileIsInvisible() throws Exception {
		UUID aliceKids = insertProfile(aliceId, "Kids");
		insertProfile(bobId, "Bob");

		mvc.perform(get("/profiles").cookie(bob))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Bob"));

		// Bob changes the id in the URL: answered like a profile that does not exist.
		for (MockHttpServletRequestBuilder request : List.of(
				put("/profiles/" + aliceKids).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"),
				delete("/profiles/" + aliceKids),
				post("/profiles/" + aliceKids + "/select"))) {
			mvc.perform(request.cookie(bob))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error").value("not_found"))
					.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		}

		assertThat(profileName(aliceKids)).isEqualTo("Kids");
		assertThat(library.calls()).as("the library is not asked to delete anything").isEmpty();
	}

	@Test
	void unknownOrMalformedIdsAnswerNotFound() throws Exception {
		for (String id : new String[] { UUID.randomUUID().toString(), "not-a-uuid" }) {
			mvc.perform(delete("/profiles/" + id).cookie(alice)).andExpect(status().isNotFound());
			mvc.perform(post("/profiles/" + id + "/select").cookie(alice)).andExpect(status().isNotFound());
		}
	}

	@Test
	void anonymousRequestsAreRefused() throws Exception {
		UUID kids = insertProfile(aliceId, "Kids");

		for (MockHttpServletRequestBuilder request : List.of(
				get("/profiles"),
				post("/profiles").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"),
				put("/profiles/" + kids).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"),
				delete("/profiles/" + kids),
				post("/profiles/" + kids + "/select"))) {
			mvc.perform(request)
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error").value("unauthenticated"));
		}
		assertThat(profileCount(aliceId)).isEqualTo(1);
		assertThat(profileName(kids)).isEqualTo("Kids");
	}

	// ---- Active profile (PRF-02) ----

	@Test
	void selectingAProfilePutsItInTheToken() throws Exception {
		UUID kids = insertProfile(aliceId, "Kids");

		MvcResult result = mvc.perform(post("/profiles/" + kids + "/select").cookie(alice))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(aliceId.toString()))
				.andExpect(jsonPath("$.profileId").value(kids.toString()))
				.andReturn();
		Cookie selected = cookieFrom(result);

		assertThat(setCookie(result)).contains("HttpOnly", "SameSite=Strict", "Max-Age=900");
		assertThat(tokens.verify(selected.getValue()).orElseThrow())
				.isEqualTo(new SessionClaims(aliceId, false, kids));
		mvc.perform(get("/auth/me").cookie(selected))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profileId").value(kids.toString()));
	}

	@Test
	void switchingProfileReplacesTheActiveOne() throws Exception {
		UUID kids = insertProfile(aliceId, "Kids");
		UUID parents = insertProfile(aliceId, "Parents");

		Cookie first = cookieFrom(mvc.perform(post("/profiles/" + kids + "/select").cookie(alice))
				.andExpect(status().isOk()).andReturn());
		Cookie second = cookieFrom(mvc.perform(post("/profiles/" + parents + "/select").cookie(first))
				.andExpect(status().isOk()).andReturn());

		assertThat(tokens.verify(second.getValue()).orElseThrow().profileId()).isEqualTo(parents);
	}

	@Test
	void disabledAccountCannotSelectAProfile() throws Exception {
		// Selecting issues a new token, so it must not extend a session that renewal would refuse.
		UUID kids = insertProfile(aliceId, "Kids");
		jdbc.update("update account set disabled = true where id = ?", aliceId);

		mvc.perform(post("/profiles/" + kids + "/select").cookie(alice))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void renewalDropsAProfileThatWasDeleted() throws Exception {
		UUID kids = insertProfile(aliceId, "Kids");
		Cookie selected = cookieFrom(mvc.perform(post("/profiles/" + kids + "/select").cookie(alice))
				.andExpect(status().isOk()).andReturn());
		jdbc.update("delete from profile where id = ?", kids);

		mvc.perform(get("/auth/me").cookie(selected))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profileId").value(nullValue()));
		MvcResult renewed = mvc.perform(post("/auth/refresh").cookie(selected))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profileId").value(nullValue()))
				.andReturn();
		assertThat(tokens.verify(cookieFrom(renewed).getValue()).orElseThrow().profileId()).isNull();
	}

	// ---- Delete: library first, then identity (consistency rules) ----

	@Test
	void deletingAProfileDeletesItsLibraryDataFirst() throws Exception {
		UUID kids = insertProfile(aliceId, "Kids");

		mvc.perform(delete("/profiles/" + kids).cookie(alice)).andExpect(status().isNoContent());

		assertThat(library.calls()).containsExactly(new FakeLibrary.Call(
				"DELETE", "/internal/profiles/" + kids + "/data", FakeLibrary.SERVICE_TOKEN));
		assertThat(profileCount(aliceId)).isZero();
	}

	@Test
	void profileIsKeptWhenTheLibraryFails() throws Exception {
		UUID kids = insertProfile(aliceId, "Kids");

		// 404: a library without the endpoint (before K3) must not lead to a half deletion.
		for (int failure : new int[] { 500, 503, 404 }) {
			library.answerWith(failure);
			mvc.perform(delete("/profiles/" + kids).cookie(alice))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error").value("service_unavailable"))
					.andExpect(jsonPath("$.message")
							.value("This part of HomeFlix is temporarily unavailable. Please try again in a moment."));
			assertThat(profileName(kids)).as("kept after a %d", failure).isEqualTo("Kids");
		}

		// The user tries again once the library is back.
		library.answerWith(204);
		mvc.perform(delete("/profiles/" + kids).cookie(alice)).andExpect(status().isNoContent());
		assertThat(profileCount(aliceId)).isZero();
	}

	@Test
	void deletingAnAccountDeletesItsProfiles() {
		insertProfile(aliceId, "Kids");

		jdbc.update("delete from account where id = ?", aliceId);

		assertThat(profileCount(aliceId)).isZero();
	}

	// ---- Helpers ----

	private ResultActions createProfile(Cookie session, String name) throws Exception {
		return mvc.perform(post("/profiles").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"));
	}

	private ResultActions rename(Cookie session, UUID profileId, String name) throws Exception {
		return mvc.perform(put("/profiles/" + profileId).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"));
	}

	private Cookie signIn(String email) throws Exception {
		return cookieFrom(mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andReturn());
	}

	private static String setCookie(MvcResult result) {
		return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
	}

	private static Cookie cookieFrom(MvcResult result) {
		String setCookie = setCookie(result);
		String prefix = SessionCookies.NAME + "=";
		assertThat(setCookie).startsWith(prefix);
		return new Cookie(SessionCookies.NAME, setCookie.substring(prefix.length(), setCookie.indexOf(';')));
	}

	private UUID insertAccount(String email) {
		return jdbc.queryForObject("insert into account (email, password_hash) values (?, ?) returning id",
				UUID.class, email, passwordHash);
	}

	private UUID insertProfile(UUID accountId, String name) {
		UUID id = jdbc.queryForObject("insert into profile (account_id, name) values (?, ?) returning id",
				UUID.class, accountId, name);
		// Keeps the oldest-first order certain even if two rows share a timestamp.
		jdbc.update("update profile set created_at = created_at + (select count(*) from profile where account_id = ?)::int "
				+ "* interval '1 second' where id = ?", accountId, id);
		return id;
	}

	private int profileCount(UUID accountId) {
		return jdbc.queryForObject("select count(*) from profile where account_id = ?", Integer.class, accountId);
	}

	private String profileName(UUID profileId) {
		return jdbc.queryForObject("select name from profile where id = ?", String.class, profileId);
	}

}
