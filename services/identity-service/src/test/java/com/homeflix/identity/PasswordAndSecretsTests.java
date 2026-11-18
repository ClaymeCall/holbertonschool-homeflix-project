package com.homeflix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.identity.account.AccountRecord;
import com.homeflix.identity.session.Credentials;
import com.homeflix.identity.setup.SetupRequest;

/**
 * Password storage and secrets (ACC-04, SEC-04, SEC-05, R-08, R-11): BCrypt strength 12,
 * never the plain password, and no password or hash in logs, exceptions or error responses.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class PasswordAndSecretsTests {

	private static final String PASSWORD = "Sup3r-Secret-Passphrase";
	private static final String EMAIL = "admin@homeflix.test";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

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

	// ---- Hashing (R-08) ----

	@Test
	void storedPasswordIsABcryptHashOfStrength12AndNotThePlainPassword() throws Exception {
		postJson("/setup/admin", body(EMAIL, PASSWORD)).andExpect(status().isCreated());

		String stored = jdbc.queryForObject("select password_hash from account where email = ?", String.class, EMAIL);
		assertThat(stored)
				.isNotEqualTo(PASSWORD)
				.doesNotContain(PASSWORD)
				.startsWith("$2a$12$")
				.hasSize(60);
		assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
	}

	@Test
	void samePasswordGivesADifferentHashEachTime() {
		// The salt is random and part of the hash, so equal passwords cannot be spotted.
		String first = passwordEncoder.encode(PASSWORD);
		String second = passwordEncoder.encode(PASSWORD);

		assertThat(first).isNotEqualTo(second);
		assertThat(passwordEncoder.matches(PASSWORD, first)).isTrue();
		assertThat(passwordEncoder.matches(PASSWORD, second)).isTrue();
	}

	// ---- Logs and error responses (SEC-04) ----

	@Test
	void logsContainNoPasswordAndNoHash(CapturedOutput output) throws Exception {
		postJson("/setup/admin", body(EMAIL, PASSWORD)).andExpect(status().isCreated());
		postJson("/setup/admin", body("other@homeflix.test", PASSWORD)).andExpect(status().isConflict());
		postJson("/auth/sign-in", body(EMAIL, PASSWORD)).andExpect(status().isOk());
		postJson("/auth/sign-in", body(EMAIL, "Wrong-" + PASSWORD)).andExpect(status().isUnauthorized());
		postJson("/auth/sign-in", body("unknown@homeflix.test", PASSWORD)).andExpect(status().isUnauthorized());

		String hash = jdbc.queryForObject("select password_hash from account where email = ?", String.class, EMAIL);
		assertThat(output.getAll())
				.doesNotContain(PASSWORD)
				.doesNotContain(hash)
				.doesNotContain("$2a$");
	}

	@Test
	void errorResponsesContainNoPasswordAndNoHash() throws Exception {
		postJson("/setup/admin", body(EMAIL, PASSWORD)).andExpect(status().isCreated());

		for (ResultActions answer : new ResultActions[] {
				postJson("/setup/admin", body("other@homeflix.test", PASSWORD)),
				postJson("/setup/admin", body("not-an-email", PASSWORD)),
				postJson("/auth/sign-in", body(EMAIL, "Wrong-" + PASSWORD)),
				postJson("/auth/sign-in", body("not-an-email", PASSWORD)),
				postJson("/auth/sign-in", "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"") }) {
			String responseBody = answer.andReturn().getResponse().getContentAsString();
			assertThat(responseBody).doesNotContain(PASSWORD).doesNotContain("$2a$");
		}
	}

	@Test
	void databaseErrorsDoNotCarryTheFailingRow() {
		// Without logServerErrorDetail=false, PostgreSQL would add
		// "Failing row contains (..., <email>, <hash>, ...)" to this message.
		String hash = passwordEncoder.encode(PASSWORD);

		assertThatThrownBy(() -> jdbc.update(
				"insert into account (email, password_hash) values (?, ?)", "Upper@HomeFlix.test", hash))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("account_email_normalized")
				.hasMessageNotContaining(hash)
				.hasMessageNotContaining("Upper@HomeFlix.test");
	}

	@Test
	void duplicateEmailErrorDoesNotCarryTheEmail() {
		String hash = passwordEncoder.encode(PASSWORD);
		jdbc.update("insert into account (email, password_hash) values (?, ?)", EMAIL, hash);

		// Without the setting: "Key (email)=(admin@homeflix.test) already exists."
		assertThatThrownBy(() -> jdbc.update(
				"insert into account (email, password_hash) values (?, ?)", EMAIL, hash))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("account_email_unique")
				.hasMessageNotContaining(EMAIL);
	}

	@Test
	void requestAndAccountObjectsNeverPrintSecrets() {
		String hash = passwordEncoder.encode(PASSWORD);

		assertThat(new SetupRequest(EMAIL, PASSWORD).toString()).doesNotContain(PASSWORD);
		assertThat(new Credentials(EMAIL, PASSWORD).toString()).doesNotContain(PASSWORD);
		assertThat(new AccountRecord(UUID.randomUUID(), EMAIL, hash, false, false).toString())
				.doesNotContain(hash)
				.doesNotContain("$2a$");
	}

	// ---- Helpers ----

	private ResultActions postJson(String path, String json) throws Exception {
		return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String body(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

}
