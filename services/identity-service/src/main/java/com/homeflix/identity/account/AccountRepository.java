package com.homeflix.identity.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Reads accounts from the identity service's own database (ARC-04).
 */
@Repository
public class AccountRepository {

	private static final String COLUMNS = "id, email, password_hash, is_admin, disabled";

	private static final RowMapper<AccountRecord> MAPPER = (rs, rowNum) -> new AccountRecord(
			rs.getObject("id", UUID.class),
			rs.getString("email"),
			rs.getString("password_hash"),
			rs.getBoolean("is_admin"),
			rs.getBoolean("disabled"));

	private final JdbcTemplate jdbc;

	public AccountRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The email must already be trimmed and lower-cased, as stored. */
	public Optional<AccountRecord> findByEmail(String email) {
		return jdbc.query("select " + COLUMNS + " from account where email = ?", MAPPER, email)
				.stream().findFirst();
	}

	public Optional<AccountRecord> findById(UUID id) {
		return jdbc.query("select " + COLUMNS + " from account where id = ?", MAPPER, id)
				.stream().findFirst();
	}

}
