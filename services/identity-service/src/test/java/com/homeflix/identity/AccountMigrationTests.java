package com.homeflix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the V2 account migration against a real PostgreSQL (ADR-0011).
 * Each test runs in a transaction that is rolled back, so tests stay independent.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AccountMigrationTests {

	private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void accountMigrationIsApplied() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where version = '2' and success", Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void migrationCreatesNoDefaultAccount() {
		// ACC-02: no default administrator or any other account after a fresh start.
		Integer accounts = jdbc.queryForObject("select count(*) from account", Integer.class);
		assertThat(accounts).isZero();
	}

	@Test
	void newAccountIsAnEnabledUserByDefault() {
		Map<String, Object> row = jdbc.queryForMap(
				"insert into account (email, password_hash) values (?, ?) returning *",
				"viewer@homeflix.test", HASH);

		assertThat(row.get("id")).isInstanceOf(UUID.class);
		assertThat(row.get("is_admin")).isEqualTo(false);
		assertThat(row.get("disabled")).isEqualTo(false);
		assertThat(row.get("created_at")).isNotNull();
		assertThat(row.get("updated_at")).isNotNull();
	}

	@Test
	void accountCanBeAdministratorAndDisabled() {
		Map<String, Object> row = jdbc.queryForMap(
				"insert into account (email, password_hash, is_admin, disabled) values (?, ?, true, true) returning *",
				"admin@homeflix.test", HASH);

		assertThat(row.get("is_admin")).isEqualTo(true);
		assertThat(row.get("disabled")).isEqualTo(true);
	}

	@Test
	void duplicateEmailIsRejected() {
		insertAccount("same@homeflix.test");

		assertThatThrownBy(() -> insertAccount("same@homeflix.test"))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("account_email_unique");
	}

	// PostgreSQL aborts the transaction after the first error, so each test
	// below triggers exactly one failing statement.

	@Test
	void upperCaseEmailIsRejected() {
		assertThatThrownBy(() -> insertAccount("Viewer@HomeFlix.test"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("account_email_normalized");
	}

	@Test
	void untrimmedEmailIsRejected() {
		assertThatThrownBy(() -> insertAccount(" viewer@homeflix.test"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("account_email_normalized");
	}

	@Test
	void blankEmailIsRejected() {
		assertThatThrownBy(() -> insertAccount(""))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("account_email_not_blank");
	}

	@Test
	void missingPasswordHashIsRejected() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into account (email) values (?)", "nohash@homeflix.test"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("password_hash");
	}

	@Test
	void blankPasswordHashIsRejected() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into account (email, password_hash) values (?, '')", "emptyhash@homeflix.test"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("account_password_hash_not_blank");
	}

	@Test
	void nullRoleIsRejected() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into account (email, password_hash, is_admin) values (?, ?, null)",
				"nullrole@homeflix.test", HASH))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("is_admin");
	}

	@Test
	void nullDisabledFlagIsRejected() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into account (email, password_hash, disabled) values (?, ?, null)",
				"nulldisabled@homeflix.test", HASH))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("disabled");
	}

	private void insertAccount(String email) {
		jdbc.update("insert into account (email, password_hash) values (?, ?)", email, HASH);
	}

}