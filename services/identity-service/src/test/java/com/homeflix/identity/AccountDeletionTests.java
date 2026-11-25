package com.homeflix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.identity.account.AccountManagementService;
import com.homeflix.identity.client.LibraryClient;
import com.homeflix.identity.security.Caller;
import com.homeflix.identity.session.SessionCookies;
import com.homeflix.identity.web.CrossSiteRequestFilter;
import com.homeflix.identity.web.ServiceUnavailableException;

import jakarta.servlet.http.Cookie;

/**
 * Deleting an account across services (K2: SEC-07, R-13, R-10, R-03), against a real PostgreSQL
 * (ADR-0011). Sessions come from a real sign-in. The library service is replaced by
 * {@link FakeLibrary}; its own side (only favourites and progress are deleted, and the video files
 * stay) is tested in the library by {@code ProfileDataDeletionTests} (K3). Not transactional: the
 * concurrency tests need committed data.
 *
 * Source media (SEC-07, R-03): the identity service has no access to any media folder (compose.yaml
 * mounts none) and calls only the library endpoint that deletes favourites and progress. The tests
 * below check that this endpoint is the only one called.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountDeletionTests {

	private static final String ADMIN = "admin@homeflix.test";
	private static final String ALICE = "alice@homeflix.test";
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
	private AccountManagementService accountManagement;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private CrossSiteRequestFilter crossSiteRequestFilter;

	private final FakeLibrary library = FakeLibrary.instance();

	private MockMvc mvc;
	private UUID adminId;
	private UUID aliceId;
	private UUID aliceKids;
	private UUID aliceParents;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(crossSiteRequestFilter).build();
		library.reset();
		if (passwordHash == null) {
			passwordHash = passwordEncoder.encode(PASSWORD);
		}
		jdbc.update("delete from account");
		adminId = insertAccount(ADMIN, true);
		aliceId = insertAccount(ALICE, false);
		aliceKids = insertProfile(aliceId, "Kids");
		aliceParents = insertProfile(aliceId, "Parents");
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from account");
		library.reset();
	}

	// ---- An administrator deletes an account ----

	@Test
	void administratorDeletesAnAccountAndTheLibraryDataOfEachProfile() throws Exception {
		Cookie admin = signIn(ADMIN);

		mvc.perform(delete("/accounts/" + aliceId).cookie(admin)).andExpect(status().isNoContent());

		// One call per profile, with the identity service's own secret, and nothing else: no other
		// endpoint of the library is ever asked to delete anything (SEC-07, R-03).
		assertThat(library.calls()).containsExactlyInAnyOrder(
				profileDataCall(aliceKids), profileDataCall(aliceParents));
		assertThat(accountExists(aliceId)).isFalse();
		assertThat(profileCount(aliceId)).isZero();

		// Gone from the list, and the email can no longer sign in.
		mvc.perform(get("/accounts").cookie(admin)).andExpect(jsonPath("$.length()").value(1));
		mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON).content(credentials(ALICE)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_credentials"));
	}

	@Test
	void accountWithoutProfilesIsDeletedWithoutCallingTheLibrary() throws Exception {
		UUID empty = insertAccount("empty@homeflix.test", false);

		mvc.perform(delete("/accounts/" + empty).cookie(signIn(ADMIN))).andExpect(status().isNoContent());

		assertThat(accountExists(empty)).isFalse();
		assertThat(library.calls()).isEmpty();
	}

	@Test
	void deletedAccountCannotRenewItsSession() throws Exception {
		// R-10: no revocation list, so renewal is where a deleted account's session ends.
		Cookie alice = signIn(ALICE);
		Cookie aliceWithProfile = cookieFrom(mvc.perform(post("/profiles/" + aliceKids + "/select").cookie(alice))
				.andExpect(status().isOk()).andReturn());

		mvc.perform(delete("/accounts/" + aliceId).cookie(signIn(ADMIN))).andExpect(status().isNoContent());

		for (Cookie session : new Cookie[] { alice, aliceWithProfile }) {
			mvc.perform(post("/auth/refresh").cookie(session))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error").value("unauthenticated"));
			mvc.perform(get("/auth/me").cookie(session)).andExpect(status().isUnauthorized());
			mvc.perform(post("/profiles/" + aliceKids + "/select").cookie(session))
					.andExpect(status().isUnauthorized());
		}
	}

	// ---- An account deletes itself ----

	@Test
	void accountDeletesItselfAndItsCookieIsCleared() throws Exception {
		Cookie alice = signIn(ALICE);

		MvcResult result = mvc.perform(delete("/auth/me").cookie(alice))
				.andExpect(status().isNoContent())
				.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
				.startsWith(SessionCookies.NAME + "=;").contains("Max-Age=0");
		assertThat(library.calls()).containsExactlyInAnyOrder(
				profileDataCall(aliceKids), profileDataCall(aliceParents));
		assertThat(accountExists(aliceId)).isFalse();
		assertThat(accountExists(adminId)).isTrue();

		// The old token is still signed, but it can no longer be renewed (R-10).
		mvc.perform(post("/auth/refresh").cookie(alice)).andExpect(status().isUnauthorized());
		// Deleting again with that token: the account is gone.
		mvc.perform(delete("/auth/me").cookie(alice))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
	}

	@Test
	void disabledAccountCannotDeleteItself() throws Exception {
		// Its token is still valid for a few minutes, but the account is read again.
		Cookie alice = signIn(ALICE);
		jdbc.update("update account set disabled = true where id = ?", aliceId);

		mvc.perform(delete("/auth/me").cookie(alice)).andExpect(status().isUnauthorized());

		assertThat(accountExists(aliceId)).isTrue();
		assertThat(library.calls()).isEmpty();
	}

	// ---- A failure of the library is visible, and nothing is lost (consistency rules) ----

	@Test
	void accountIsKeptWhenTheLibraryFails() throws Exception {
		Cookie admin = signIn(ADMIN);

		// 404: a library without the endpoint must not lead to an account deleted with its data left behind.
		for (int failure : new int[] { 500, 503, 404, 401 }) {
			library.answerWith(failure);
			mvc.perform(delete("/accounts/" + aliceId).cookie(admin))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error").value("service_unavailable"))
					.andExpect(jsonPath("$.message")
							.value("This part of HomeFlix is temporarily unavailable. Please try again in a moment."));
			assertThat(accountExists(aliceId)).as("kept after a %d", failure).isTrue();
			assertThat(profileCount(aliceId)).isEqualTo(2);
		}
		// The account still works while it waits.
		signIn(ALICE);

		// The administrator tries again once the library is back.
		library.answerWith(204);
		mvc.perform(delete("/accounts/" + aliceId).cookie(admin)).andExpect(status().isNoContent());
		assertThat(accountExists(aliceId)).isFalse();
	}

	@Test
	void failureOnTheSecondProfileStopsTheDeletion() throws Exception {
		Cookie alice = signIn(ALICE);
		library.answerWith(503, 1);

		mvc.perform(delete("/auth/me").cookie(alice))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		// The first call is not undone and need not be (idempotent): that profile is simply empty.
		assertThat(library.calls()).hasSize(2);
		assertThat(accountExists(aliceId)).isTrue();
		assertThat(profileCount(aliceId)).isEqualTo(2);

		// Repeating the request asks again for every profile, then deletes.
		library.answerWith(204);
		mvc.perform(delete("/auth/me").cookie(alice)).andExpect(status().isNoContent());
		assertThat(accountExists(aliceId)).isFalse();
		assertThat(library.calls()).hasSize(4);
	}

	@Test
	void stoppedLibraryAnswersServiceUnavailable() {
		// A closed port: connection refused, as when the library container is stopped.
		Caller admin = new Caller(adminId, true, null);
		LibraryClient stopped = new LibraryClient("http://127.0.0.1:1", FakeLibrary.SERVICE_TOKEN,
				Duration.ofSeconds(2), Duration.ofSeconds(5));
		AccountManagementService withStoppedLibrary = new AccountManagementService(jdbc, passwordEncoder, stopped,
				transactionManager);

		assertThatThrownBy(() -> withStoppedLibrary.delete(admin, aliceId))
				.isInstanceOf(ServiceUnavailableException.class);
		assertThat(accountExists(aliceId)).isTrue();
		assertThat(profileCount(aliceId)).isEqualTo(2);
	}

	@Test
	void profileCreatedDuringTheDeletionHasItsDataDeletedToo() throws Exception {
		// While the library is being called no lock is held, so a profile can still be created.
		// It must not be deleted with the account without its data being cleared first.
		UUID[] created = new UUID[1];
		library.onNextCall(() -> created[0] = insertProfile(aliceId, "Created meanwhile"));

		mvc.perform(delete("/accounts/" + aliceId).cookie(signIn(ADMIN))).andExpect(status().isNoContent());

		assertThat(created[0]).isNotNull();
		assertThat(library.calls()).containsExactlyInAnyOrder(
				profileDataCall(aliceKids), profileDataCall(aliceParents), profileDataCall(created[0]));
		assertThat(accountExists(aliceId)).isFalse();
	}

	// ---- Last administrator ----

	@Test
	void lastAdministratorCannotBeDeleted() throws Exception {
		Cookie admin = signIn(ADMIN);
		insertProfile(adminId, "Me");

		mvc.perform(delete("/accounts/" + adminId).cookie(admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("last_administrator"));
		mvc.perform(delete("/auth/me").cookie(admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("last_administrator"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		// Refused before any call: the administrator's own data is not emptied for nothing.
		assertThat(library.calls()).isEmpty();
		assertThat(accountExists(adminId)).isTrue();
		assertThat(profileCount(adminId)).isEqualTo(1);
	}

	@Test
	void anAdministratorCanBeDeletedWhileAnotherRemains() throws Exception {
		UUID second = insertAccount("second-admin@homeflix.test", true);
		Cookie admin = signIn(ADMIN);

		mvc.perform(delete("/accounts/" + second).cookie(admin)).andExpect(status().isNoContent());
		insertAccount("third-admin@homeflix.test", true);
		// Deleting itself is allowed too once another administrator exists.
		mvc.perform(delete("/auth/me").cookie(admin)).andExpect(status().isNoContent());

		assertThat(activeAdministrators()).isEqualTo(1);
	}

	@Test
	void administratorsDeletingEachOtherAtOnceLeaveOne() throws Exception {
		UUID second = insertAccount("second-admin@homeflix.test", true);
		Caller first = new Caller(adminId, true, null);
		Caller other = new Caller(second, true, null);

		runTogether(
				() -> accountManagement.delete(first, second),
				() -> accountManagement.delete(other, adminId));

		assertThat(activeAdministrators()).isEqualTo(1);
	}

	// ---- Who may delete (SEC-02) ----

	@Test
	void userCannotDeleteAnotherAccount() throws Exception {
		Cookie alice = signIn(ALICE);
		UUID bob = insertAccount("bob@homeflix.test", false);

		for (UUID target : new UUID[] { bob, adminId, aliceId }) {
			mvc.perform(delete("/accounts/" + target).cookie(alice))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error").value("forbidden"));
		}
		assertThat(accountExists(bob)).isTrue();
		assertThat(accountExists(aliceId)).isTrue();
		assertThat(library.calls()).isEmpty();
	}

	@Test
	void anonymousAndCrossSiteRequestsAreRefused() throws Exception {
		mvc.perform(delete("/accounts/" + aliceId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
		mvc.perform(delete("/auth/me")).andExpect(status().isUnauthorized());

		mvc.perform(delete("/accounts/" + aliceId).cookie(signIn(ADMIN)).header("Sec-Fetch-Site", "cross-site"))
				.andExpect(status().isForbidden());
		mvc.perform(delete("/auth/me").cookie(signIn(ALICE)).header("Sec-Fetch-Site", "cross-site"))
				.andExpect(status().isForbidden());

		assertThat(accountExists(aliceId)).isTrue();
		assertThat(library.calls()).isEmpty();
	}

	@Test
	void unknownAccountAnswersNotFound() throws Exception {
		Cookie admin = signIn(ADMIN);

		for (String id : new String[] { UUID.randomUUID().toString(), "not-a-uuid" }) {
			mvc.perform(delete("/accounts/" + id).cookie(admin))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error").value("not_found"));
		}
		assertThat(library.calls()).isEmpty();
	}

	// ---- Helpers ----

	private static FakeLibrary.Call profileDataCall(UUID profileId) {
		return new FakeLibrary.Call("DELETE", "/internal/profiles/" + profileId + "/data", FakeLibrary.SERVICE_TOKEN);
	}

	/** Runs the actions at the same moment and waits for all of them; failures are expected. */
	private static void runTogether(Runnable... actions) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(actions.length);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (Runnable action : actions) {
				futures.add(pool.submit(() -> {
					start.await();
					try {
						action.run();
					}
					catch (RuntimeException ex) {
						// One of them is refused: no longer an administrator, or the last one.
					}
					return null;
				}));
			}
			start.countDown();
			for (Future<?> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	private Cookie signIn(String email) throws Exception {
		return cookieFrom(mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email)))
				.andExpect(status().isOk())
				.andReturn());
	}

	private static String credentials(String email) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
	}

	private static Cookie cookieFrom(MvcResult result) {
		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		String prefix = SessionCookies.NAME + "=";
		assertThat(setCookie).startsWith(prefix);
		return new Cookie(SessionCookies.NAME, setCookie.substring(prefix.length(), setCookie.indexOf(';')));
	}

	private UUID insertAccount(String email, boolean admin) {
		return jdbc.queryForObject(
				"insert into account (email, password_hash, is_admin) values (?, ?, ?) returning id",
				UUID.class, email, passwordHash, admin);
	}

	private UUID insertProfile(UUID accountId, String name) {
		return jdbc.queryForObject("insert into profile (account_id, name) values (?, ?) returning id",
				UUID.class, accountId, name);
	}

	private boolean accountExists(UUID accountId) {
		return jdbc.queryForObject("select count(*) from account where id = ?", Integer.class, accountId) == 1;
	}

	private int profileCount(UUID accountId) {
		return jdbc.queryForObject("select count(*) from profile where account_id = ?", Integer.class, accountId);
	}

	private int activeAdministrators() {
		return jdbc.queryForObject("select count(*) from account where is_admin and not disabled", Integer.class);
	}

}
