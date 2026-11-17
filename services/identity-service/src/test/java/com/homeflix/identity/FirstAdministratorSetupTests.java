package com.homeflix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.identity.setup.SetupAlreadyCompletedException;
import com.homeflix.identity.setup.SetupRequest;
import com.homeflix.identity.setup.SetupService;

/**
 * First-run setup (ACC-01, ACC-02, R-07) against a real PostgreSQL (ADR-0011).
 * Not transactional: the concurrency test needs committed data, so the account
 * table is emptied before and after each test instead.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FirstAdministratorSetupTests {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private SetupService setupService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from account");
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from account");
	}

	// ---- Status ----

	@Test
	void setupIsRequiredWhenNoAdministratorExists() throws Exception {
		mvc.perform(get("/setup/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.setupRequired").value(true));
	}

	@Test
	void setupIsNotRequiredOnceAnAdministratorExists() throws Exception {
		insertAccount("admin@homeflix.test", true);

		mvc.perform(get("/setup/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.setupRequired").value(false));
	}

	// ---- Success, then rejection (issue "Done when") ----

	@Test
	void setupSucceedsOnceAndSecondAttemptIsRejected() throws Exception {
		setup("first@homeflix.test", PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.email").value("first@homeflix.test"))
				.andExpect(jsonPath("$.admin").value(true));

		setup("second@homeflix.test", PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("setup_already_completed"));

		assertThat(countAdministrators()).isEqualTo(1);
		assertThat(countAccounts("second@homeflix.test")).isZero();
	}

	@Test
	void setupIsRejectedWhenAnAdministratorAlreadyExists() throws Exception {
		insertAccount("existing-admin@homeflix.test", true);

		setup("intruder@homeflix.test", PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("setup_already_completed"));

		assertThat(countAccounts("intruder@homeflix.test")).isZero();
	}

	@Test
	void ordinaryUserAccountDoesNotCloseSetup() throws Exception {
		// Setup is closed only by an administrator, not by any account.
		insertAccount("viewer@homeflix.test", false);

		setup("admin@homeflix.test", PASSWORD).andExpect(status().isCreated());
	}

	@Test
	void concurrentSetupsCreateOnlyOneAdministrator() throws Exception {
		// R-07: two people submitting the setup form at the same moment.
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (String email : List.of("a@homeflix.test", "b@homeflix.test")) {
				results.add(pool.submit(() -> {
					start.await();
					try {
						setupService.createFirstAdministrator(new SetupRequest(email, PASSWORD));
						return true;
					}
					catch (SetupAlreadyCompletedException ex) {
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
			assertThat(countAdministrators()).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	// ---- What is stored ----

	@Test
	void passwordIsStoredAsBcryptHashAndNeverReturned() throws Exception {
		setup("admin@homeflix.test", PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(content().string(not(containsString("$2"))));

		Map<String, Object> row = jdbc.queryForMap("select * from account where email = 'admin@homeflix.test'");
		String storedHash = (String) row.get("password_hash");
		assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("$2");
		assertThat(passwordEncoder.matches(PASSWORD, storedHash)).isTrue();
		assertThat(row.get("is_admin")).isEqualTo(true);
		assertThat(row.get("disabled")).isEqualTo(false);
	}

	@Test
	void emailIsTrimmedAndLowerCased() throws Exception {
		setup("  Admin@HomeFlix.TEST ", PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("admin@homeflix.test"));

		assertThat(countAccounts("admin@homeflix.test")).isEqualTo(1);
	}

	// ---- Server-side validation (SEC-01) and safe errors (SEC-04) ----

	@Test
	void invalidEmailIsRejected() throws Exception {
		setup("not-an-email", PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields.email").exists());
		assertThat(countAdministrators()).isZero();
	}

	@Test
	void missingEmailIsRejected() throws Exception {
		postJson("{\"password\":\"" + PASSWORD + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.email").exists());
	}

	@Test
	void missingPasswordIsRejected() throws Exception {
		postJson("{\"email\":\"admin@homeflix.test\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.password").exists());
	}

	@Test
	void shortPasswordIsRejected() throws Exception {
		setup("admin@homeflix.test", "short")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.password").value(containsString("12")));
		assertThat(countAdministrators()).isZero();
	}

	@Test
	void passwordLongerThanBcryptLimitIsRejected() throws Exception {
		// 40 characters but 80 bytes: BCrypt would silently ignore the end.
		setup("admin@homeflix.test", "é".repeat(40))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.password").exists());
		assertThat(countAdministrators()).isZero();
	}

	@Test
	void emailAlreadyUsedByAnotherAccountIsRejected() throws Exception {
		insertAccount("taken@homeflix.test", false);

		setup("taken@homeflix.test", PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.email").exists());
		assertThat(countAdministrators()).isZero();
	}

	@Test
	void malformedJsonIsRejectedWithoutTechnicalDetails() throws Exception {
		postJson("{not json")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"))
				.andExpect(content().string(not(containsString("Exception"))))
				.andExpect(content().string(not(containsString("jackson"))));
	}

	// ---- Helpers ----

	private ResultActions setup(String email, String password) throws Exception {
		return postJson("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private ResultActions postJson(String body) throws Exception {
		return mvc.perform(post("/setup/admin").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void insertAccount(String email, boolean admin) {
		jdbc.update("insert into account (email, password_hash, is_admin) values (?, ?, ?)", email, HASH, admin);
	}

	private int countAdministrators() {
		return jdbc.queryForObject("select count(*) from account where is_admin", Integer.class);
	}

	private int countAccounts(String email) {
		return jdbc.queryForObject("select count(*) from account where email = ?", Integer.class, email);
	}

}