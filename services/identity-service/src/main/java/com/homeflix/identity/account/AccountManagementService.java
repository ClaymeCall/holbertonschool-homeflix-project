package com.homeflix.identity.account;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeflix.identity.security.Caller;
import com.homeflix.identity.security.PasswordConfig;
import com.homeflix.identity.web.ForbiddenException;
import com.homeflix.identity.web.InvalidInputException;
import com.homeflix.identity.web.NotFoundException;
import com.homeflix.identity.web.UnauthenticatedException;

/**
 * Account management by administrators (ACC-09, ACC-10, SEC-02): list, create, change the role,
 * disable and re-enable. Deleting an account is K2 (consistency rules).
 *
 * Every operation runs under one PostgreSQL advisory lock, so changes to administrators happen
 * one after the other. That makes the last-administrator rule hold under simultaneous requests:
 * two administrators demoting each other at the same moment still leave one.
 *
 * The caller is read again from the database under that lock: a disabled or demoted administrator
 * cannot undo the change with a token that is still valid (identity design).
 */
@Service
public class AccountManagementService {

	private static final Logger log = LoggerFactory.getLogger(AccountManagementService.class);

	/**
	 * Key of the advisory lock on account changes. Arbitrary but must stay the same, and differ
	 * from the setup lock.
	 */
	private static final long ACCOUNTS_LOCK_KEY = 4_206_660_002L;

	private static final String COLUMNS = "id, email, is_admin, disabled, created_at";

	private static final RowMapper<ManagedAccount> MAPPER = (rs, rowNum) -> new ManagedAccount(
			rs.getObject("id", UUID.class),
			rs.getString("email"),
			rs.getBoolean("is_admin"),
			rs.getBoolean("disabled"),
			rs.getObject("created_at", OffsetDateTime.class).toInstant());

	private final JdbcTemplate jdbc;
	private final PasswordEncoder passwordEncoder;

	public AccountManagementService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
	}

	/** Oldest first. The id breaks ties, so pages never overlap. */
	@Transactional
	public List<ManagedAccount> list(Caller caller, int limit, int offset) {
		lockAndCheckAdministrator(caller);
		return jdbc.query("select " + COLUMNS + " from account order by created_at, id limit ? offset ?",
				MAPPER, limit, offset);
	}

	@Transactional
	public ManagedAccount create(Caller caller, NewAccountRequest request) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > PasswordConfig.BCRYPT_MAX_BYTES) {
			throw new InvalidInputException("password", "The password is too long.");
		}
		lockAndCheckAdministrator(caller);

		String hash = passwordEncoder.encode(request.password());
		ManagedAccount created;
		try {
			created = jdbc.queryForObject(
					"insert into account (email, password_hash, is_admin) values (?, ?, ?) returning " + COLUMNS,
					MAPPER, request.email(), hash, request.admin());
		}
		catch (DuplicateKeyException ex) {
			throw new InvalidInputException("email", "This email address is already used by another account.");
		}
		log.info("Account {} created by {} (admin={})", created.id(), caller.accountId(), created.admin());
		return created;
	}

	@Transactional
	public ManagedAccount changeRole(Caller caller, UUID accountId, boolean admin) {
		lockAndCheckAdministrator(caller);
		ManagedAccount account = find(accountId);
		return update(caller, account, admin, account.disabled());
	}

	@Transactional
	public ManagedAccount setDisabled(Caller caller, UUID accountId, boolean disabled) {
		lockAndCheckAdministrator(caller);
		ManagedAccount account = find(accountId);
		return update(caller, account, account.admin(), disabled);
	}

	private ManagedAccount update(Caller caller, ManagedAccount account, boolean admin, boolean disabled) {
		if (account.admin() == admin && account.disabled() == disabled) {
			return account;
		}
		boolean wasActiveAdministrator = account.admin() && !account.disabled();
		boolean staysActiveAdministrator = admin && !disabled;
		if (wasActiveAdministrator && !staysActiveAdministrator && activeAdministrators() <= 1) {
			throw new LastAdministratorException();
		}

		ManagedAccount updated = jdbc.queryForObject(
				"update account set is_admin = ?, disabled = ?, updated_at = now() where id = ? returning " + COLUMNS,
				MAPPER, admin, disabled, account.id());
		log.info("Account {} changed by {}: admin={}, disabled={}",
				account.id(), caller.accountId(), updated.admin(), updated.disabled());
		return updated;
	}

	/**
	 * Takes the lock (held until the transaction ends), then checks that the caller is still an
	 * enabled administrator in the database, not only in its token.
	 */
	private void lockAndCheckAdministrator(Caller caller) {
		jdbc.queryForObject("select 1 from pg_advisory_xact_lock(?)", Integer.class, ACCOUNTS_LOCK_KEY);

		List<ManagedAccount> found = jdbc.query("select " + COLUMNS + " from account where id = ?",
				MAPPER, caller.accountId());
		if (found.isEmpty() || found.get(0).disabled()) {
			throw new UnauthenticatedException();
		}
		if (!found.get(0).admin()) {
			throw new ForbiddenException();
		}
	}

	private ManagedAccount find(UUID accountId) {
		return jdbc.query("select " + COLUMNS + " from account where id = ?", MAPPER, accountId)
				.stream().findFirst()
				.orElseThrow(NotFoundException::new);
	}

	private int activeAdministrators() {
		Integer count = jdbc.queryForObject(
				"select count(*) from account where is_admin and not disabled", Integer.class);
		return count == null ? 0 : count;
	}

}
