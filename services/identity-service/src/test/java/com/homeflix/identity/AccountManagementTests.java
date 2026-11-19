package com.homeflix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import com.homeflix.identity.account.AccountManagementService;
import com.homeflix.identity.account.LastAdministratorException;
import com.homeflix.identity.security.Caller;
import com.homeflix.identity.session.SessionCookies;
import com.homeflix.identity.web.CrossSiteRequestFilter;
import com.homeflix.identity.web.ForbiddenException;

import jakarta.servlet.http.Cookie;

/**
 * Account management by administrators (G8: ACC-09, ACC-10, SEC-02, R-10) against a real
 * PostgreSQL (ADR-0011). Sessions come from a real sign-in. Not transactional: the concurrency
 * test needs committed data, so the account table is emptied before and after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountManagementTests {

	private static final String ADMIN_EMAIL = "admin@homeflix.test";
	private static final String USER_EMAIL = "viewer@homeflix.test";
	private static final String PASSWORD = "correct-horse-battery";
	private static final String NEW_PASSWORD = "another-long-passphrase";

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
	private CrossSiteRequestFilter crossSiteRequestFilter;

	private MockMvc mvc;
	private UUID adminId;
	private UUID userId;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(crossSiteRequestFilter).build();
		if (passwordHash == null) {
			passwordHash = passwordEncoder.encode(PASSWORD);
		}
		jdbc.update("delete from account");
		adminId = insertAccount(ADMIN_EMAIL, true);
		userId = insertAccount(USER_EMAIL, false);
		// The list is oldest first: make the order certain even if both rows share a timestamp.
		jdbc.update("update account set created_at = created_at + interval '1 second' where id = ?", userId);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from account");
	}

	// ---- Who may manage accounts (SEC-02, AS-07.2) ----

	@Test
	void anonymousRequestsAreRefusedAndChangeNothing() throws Exception {
		for (MockHttpServletRequestBuilder request : everyOperationOn(userId)) {
			mvc.perform(request)
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error").value("unauthenticated"));
		}
		expectNothingChanged();
	}

	@Test
	void userRequestsAreForbiddenAndChangeNothing() throws Exception {
		Cookie user = signIn(USER_EMAIL, PASSWORD);
		UUID other = insertAccount("other@homeflix.test", false);

		for (MockHttpServletRequestBuilder request : everyOperationOn(other)) {
			mvc.perform(request.cookie(user))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error").value("forbidden"));
		}
		// Not even on its own account.
		mvc.perform(put("/accounts/" + userId + "/role").cookie(user)
						.contentType(MediaType.APPLICATION_JSON).content("{\"admin\":true}"))
				.andExpect(status().isForbidden());

		assertThat(countAccounts()).isEqualTo(3);
		assertThat(isAdmin(userId)).isFalse();
		assertThat(isDisabled(other)).isFalse();
	}

	@Test
	void administratorDisabledSinceSignInIsRefusedAtOnce() throws Exception {
		// The token still says admin, but the database is read again (identity design).
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);
		insertAccount("second-admin@homeflix.test", true);
		jdbc.update("update account set disabled = true where id = ?", adminId);

		mvc.perform(post("/accounts/" + adminId + "/enable").cookie(admin))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
		assertThat(isDisabled(adminId)).isTrue();
	}

	@Test
	void administratorDemotedSinceSignInIsRefusedAtOnce() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);
		insertAccount("second-admin@homeflix.test", true);
		jdbc.update("update account set is_admin = false where id = ?", adminId);

		mvc.perform(put("/accounts/" + adminId + "/role").cookie(admin)
						.contentType(MediaType.APPLICATION_JSON).content("{\"admin\":true}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
		assertThat(isAdmin(adminId)).isFalse();
	}

	@Test
	void crossSiteRequestIsRefused() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		mvc.perform(post("/accounts/" + userId + "/disable").cookie(admin).header("Sec-Fetch-Site", "cross-site"))
				.andExpect(status().isForbidden());
		assertThat(isDisabled(userId)).isFalse();
	}

	// ---- List ----

	@Test
	void administratorListsTheAccountsWithoutSecrets() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		mvc.perform(get("/accounts").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(adminId.toString()))
				.andExpect(jsonPath("$[0].email").value(ADMIN_EMAIL))
				.andExpect(jsonPath("$[0].admin").value(true))
				.andExpect(jsonPath("$[0].disabled").value(false))
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$[1].id").value(userId.toString()))
				.andExpect(jsonPath("$[0].passwordHash").doesNotExist())
				.andExpect(content().string(not(containsString("$2"))));
	}

	@Test
	void listIsPaged() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		mvc.perform(get("/accounts").param("limit", "1").param("offset", "1").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(userId.toString()));

		for (String[] wrong : new String[][] { { "limit", "0" }, { "limit", "101" }, { "offset", "-1" },
				{ "limit", "abc" } }) {
			mvc.perform(get("/accounts").param(wrong[0], wrong[1]).cookie(admin))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("validation_failed"))
					.andExpect(jsonPath("$.fields." + wrong[0]).exists());
		}
	}

	// ---- Create ----

	@Test
	void administratorCreatesAUserWhoCanSignIn() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		MvcResult result = createAccount(admin, "  New.User@HomeFlix.TEST ", NEW_PASSWORD, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.email").value("new.user@homeflix.test"))
				.andExpect(jsonPath("$.admin").value(false))
				.andExpect(jsonPath("$.disabled").value(false))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(content().string(not(containsString(NEW_PASSWORD))))
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("$2");

		String stored = jdbc.queryForObject("select password_hash from account where email = ?",
				String.class, "new.user@homeflix.test");
		assertThat(stored).startsWith("$2a$12$").doesNotContain(NEW_PASSWORD);

		mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
						.content(credentials("new.user@homeflix.test", NEW_PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.admin").value(false));
	}

	@Test
	void administratorCreatesAnotherAdministrator() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		createAccount(admin, "second-admin@homeflix.test", NEW_PASSWORD, true)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.admin").value(true));

		assertThat(signInBody("second-admin@homeflix.test", NEW_PASSWORD)).contains("\"admin\":true");
	}

	@Test
	void invalidNewAccountsAreRejected() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		Map<String, String[]> invalid = Map.of(
				"email", new String[] { "not-an-email", NEW_PASSWORD },
				"password", new String[] { "short@homeflix.test", "short" });
		for (Map.Entry<String, String[]> entry : invalid.entrySet()) {
			createAccount(admin, entry.getValue()[0], entry.getValue()[1], null)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("validation_failed"))
					.andExpect(jsonPath("$.fields." + entry.getKey()).exists());
		}
		// 40 characters but 80 bytes: BCrypt would silently ignore the end.
		createAccount(admin, "long@homeflix.test", "é".repeat(40), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.password").exists());
		// Already used, whatever the case.
		createAccount(admin, "Viewer@HomeFlix.test", NEW_PASSWORD, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.email").value("This email address is already used by another account."));

		assertThat(countAccounts()).isEqualTo(2);
	}

	// ---- Role ----

	@Test
	void administratorPromotesAndDemotesAUser() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		changeRole(admin, userId, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.admin").value(true));
		assertThat(isAdmin(userId)).isTrue();
		assertThat(signInBody(USER_EMAIL, PASSWORD)).contains("\"admin\":true");

		changeRole(admin, userId, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.admin").value(false));
		assertThat(isAdmin(userId)).isFalse();
	}

	@Test
	void settingTheSameRoleChangesNothing() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		changeRole(admin, userId, false).andExpect(status().isOk()).andExpect(jsonPath("$.admin").value(false));
		// The only administrator is already one: not a demotion, so allowed.
		changeRole(admin, adminId, true).andExpect(status().isOk()).andExpect(jsonPath("$.admin").value(true));
	}

	@Test
	void roleRequestWithoutTheRoleIsRejected() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		mvc.perform(put("/accounts/" + userId + "/role").cookie(admin)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.admin").exists());
	}

	// ---- Disable and re-enable (ACC-10, R-10, AS-07.6) ----

	@Test
	void disabledAccountCannotSignInNorRenewUntilReEnabled() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);
		Cookie userSession = signIn(USER_EMAIL, PASSWORD);

		mvc.perform(post("/accounts/" + userId + "/disable").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disabled").value(true));

		// Same answer as a wrong password, so the account is not revealed.
		mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON).content(credentials(USER_EMAIL, PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_credentials"));
		// The session it already had cannot be renewed, so it ends within 15 minutes.
		mvc.perform(post("/auth/refresh").cookie(userSession))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
		mvc.perform(get("/auth/me").cookie(userSession)).andExpect(status().isUnauthorized());

		mvc.perform(post("/accounts/" + userId + "/enable").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disabled").value(false));
		signIn(USER_EMAIL, PASSWORD);
	}

	@Test
	void disablingTwiceChangesNothing() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		mvc.perform(post("/accounts/" + userId + "/disable").cookie(admin)).andExpect(status().isOk());
		mvc.perform(post("/accounts/" + userId + "/disable").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disabled").value(true));
		mvc.perform(post("/accounts/" + adminId + "/enable").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disabled").value(false));
	}

	// ---- Unknown accounts ----

	@Test
	void unknownAccountAnswersNotFound() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		for (String id : new String[] { UUID.randomUUID().toString(), "not-a-uuid" }) {
			for (MockHttpServletRequestBuilder request : List.of(
					post("/accounts/" + id + "/disable"),
					post("/accounts/" + id + "/enable"),
					put("/accounts/" + id + "/role").contentType(MediaType.APPLICATION_JSON).content("{\"admin\":true}"))) {
				mvc.perform(request.cookie(admin))
						.andExpect(status().isNotFound())
						.andExpect(jsonPath("$.error").value("not_found"));
			}
		}
	}

	// ---- Last administrator ----

	@Test
	void onlyAdministratorCannotDemoteOrDisableItself() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);

		changeRole(admin, adminId, false)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("last_administrator"))
				.andExpect(jsonPath("$.message").value(
						"HomeFlix needs at least one administrator. Make another account an administrator first."));
		mvc.perform(post("/accounts/" + adminId + "/disable").cookie(admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("last_administrator"));

		assertThat(isAdmin(adminId)).isTrue();
		assertThat(isDisabled(adminId)).isFalse();
	}

	@Test
	void anAdministratorCanBeDemotedWhileAnotherRemains() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);
		UUID second = insertAccount("second-admin@homeflix.test", true);

		changeRole(admin, second, false).andExpect(status().isOk());
		changeRole(admin, userId, true).andExpect(status().isOk());
		// Disabling itself is allowed too once another administrator exists.
		mvc.perform(post("/accounts/" + adminId + "/disable").cookie(admin)).andExpect(status().isOk());

		assertThat(activeAdministrators()).isEqualTo(1);
	}

	@Test
	void disabledAdministratorDoesNotCount() throws Exception {
		Cookie admin = signIn(ADMIN_EMAIL, PASSWORD);
		UUID disabledAdmin = insertAccount("disabled-admin@homeflix.test", true);
		jdbc.update("update account set disabled = true where id = ?", disabledAdmin);

		changeRole(admin, adminId, false)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("last_administrator"));
		// Re-enabling it gives a second administrator, then the change is allowed.
		mvc.perform(post("/accounts/" + disabledAdmin + "/enable").cookie(admin)).andExpect(status().isOk());
		changeRole(admin, adminId, false).andExpect(status().isOk());
	}

	@Test
	void administratorsDemotingEachOtherAtOnceLeaveOne() throws Exception {
		UUID second = insertAccount("second-admin@homeflix.test", true);
		Caller first = new Caller(adminId, true, null);
		Caller other = new Caller(second, true, null);

		List<Throwable> outcomes = runTogether(
				() -> accountManagement.changeRole(first, second, false),
				() -> accountManagement.changeRole(other, adminId, false));

		assertThat(activeAdministrators()).isEqualTo(1);
		// One wins; the other is refused, as no longer an administrator or as the last one.
		assertThat(outcomes).filteredOn(outcome -> outcome == null).hasSize(1);
		assertThat(outcomes).filteredOn(outcome -> outcome != null)
				.allMatch(outcome -> outcome instanceof ForbiddenException
						|| outcome instanceof LastAdministratorException);
	}

	@Test
	void administratorsDisablingEachOtherAtOnceLeaveOne() throws Exception {
		UUID second = insertAccount("second-admin@homeflix.test", true);
		Caller first = new Caller(adminId, true, null);
		Caller other = new Caller(second, true, null);

		runTogether(
				() -> accountManagement.setDisabled(first, second, true),
				() -> accountManagement.setDisabled(other, adminId, true));

		assertThat(activeAdministrators()).isEqualTo(1);
	}

	// ---- Helpers ----

	private List<MockHttpServletRequestBuilder> everyOperationOn(UUID accountId) {
		return List.of(
				get("/accounts"),
				post("/accounts").contentType(MediaType.APPLICATION_JSON)
						.content(newAccount("created@homeflix.test", NEW_PASSWORD, true)),
				put("/accounts/" + accountId + "/role").contentType(MediaType.APPLICATION_JSON)
						.content("{\"admin\":true}"),
				post("/accounts/" + accountId + "/disable"),
				post("/accounts/" + accountId + "/enable"));
	}

	private void expectNothingChanged() {
		assertThat(countAccounts()).isEqualTo(2);
		assertThat(isAdmin(userId)).isFalse();
		assertThat(isDisabled(userId)).isFalse();
	}

	/** Runs both actions at the same moment; returns null for a success, else what was thrown. */
	private static List<Throwable> runTogether(Runnable... actions) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(actions.length);
		try {
			List<Future<Throwable>> futures = new ArrayList<>();
			for (Runnable action : actions) {
				futures.add(pool.submit(() -> {
					start.await();
					try {
						action.run();
						return null;
					}
					catch (RuntimeException ex) {
						return ex;
					}
				}));
			}
			start.countDown();
			List<Throwable> outcomes = new ArrayList<>();
			for (Future<Throwable> future : futures) {
				outcomes.add(future.get(30, TimeUnit.SECONDS));
			}
			return outcomes;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private Cookie signIn(String email, String password) throws Exception {
		MvcResult result = mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		String prefix = SessionCookies.NAME + "=";
		assertThat(setCookie).startsWith(prefix);
		return new Cookie(SessionCookies.NAME, setCookie.substring(prefix.length(), setCookie.indexOf(';')));
	}

	private String signInBody(String email, String password) throws Exception {
		return mvc.perform(post("/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private ResultActions createAccount(Cookie session, String email, String password, Boolean admin) throws Exception {
		return mvc.perform(post("/accounts").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(newAccount(email, password, admin)));
	}

	private ResultActions changeRole(Cookie session, UUID accountId, boolean admin) throws Exception {
		return mvc.perform(put("/accounts/" + accountId + "/role").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"admin\":" + admin + "}"));
	}

	private static String newAccount(String email, String password, Boolean admin) {
		String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"";
		return admin == null ? body + "}" : body + ",\"admin\":" + admin + "}";
	}

	private static String credentials(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private UUID insertAccount(String email, boolean admin) {
		return jdbc.queryForObject(
				"insert into account (email, password_hash, is_admin) values (?, ?, ?) returning id",
				UUID.class, email, passwordHash, admin);
	}

	private int countAccounts() {
		return jdbc.queryForObject("select count(*) from account", Integer.class);
	}

	private int activeAdministrators() {
		return jdbc.queryForObject("select count(*) from account where is_admin and not disabled", Integer.class);
	}

	private boolean isAdmin(UUID id) {
		return jdbc.queryForObject("select is_admin from account where id = ?", Boolean.class, id);
	}

	private boolean isDisabled(UUID id) {
		return jdbc.queryForObject("select disabled from account where id = ?", Boolean.class, id);
	}

}
