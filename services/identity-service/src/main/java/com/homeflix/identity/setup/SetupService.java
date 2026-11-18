package com.homeflix.identity.setup;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeflix.identity.security.PasswordConfig;
import com.homeflix.identity.web.InvalidInputException;

/**
 * First-run setup: creates the first administrator, only while no administrator exists
 * (ACC-01, ACC-02, R-07). The check is made on the server, whatever the interface shows.
 */
@Service
public class SetupService {

	private static final Logger log = LoggerFactory.getLogger(SetupService.class);

	/**
	 * Key of the PostgreSQL advisory lock taken during setup. Two setup requests arriving
	 * at the same time are handled one after the other, so only one of them can create
	 * an administrator. The value is arbitrary but must stay the same.
	 */
	private static final long SETUP_LOCK_KEY = 4_206_660_001L;

	private final JdbcTemplate jdbc;
	private final PasswordEncoder passwordEncoder;

	public SetupService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public SetupStatus status() {
		return new SetupStatus(!administratorExists());
	}

	@Transactional
	public CreatedAdministrator createFirstAdministrator(SetupRequest request) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > PasswordConfig.BCRYPT_MAX_BYTES) {
			throw new InvalidInputException("password", "The password is too long.");
		}

		// Held until the transaction ends: no other setup can check or insert meanwhile.
		jdbc.queryForObject("select 1 from pg_advisory_xact_lock(?)", Integer.class, SETUP_LOCK_KEY);

		if (administratorExists()) {
			throw new SetupAlreadyCompletedException();
		}

		String hash = passwordEncoder.encode(request.password());
		UUID id;
		try {
			id = jdbc.queryForObject(
					"insert into account (email, password_hash, is_admin) values (?, ?, true) returning id",
					UUID.class, request.email(), hash);
		}
		catch (DuplicateKeyException ex) {
			throw new InvalidInputException("email", "This email address is already used by another account.");
		}

		log.info("First administrator created (account id {})", id);
		return new CreatedAdministrator(id, request.email(), true);
	}

	private boolean administratorExists() {
		Boolean exists = jdbc.queryForObject("select exists (select 1 from account where is_admin)", Boolean.class);
		return Boolean.TRUE.equals(exists);
	}

}
