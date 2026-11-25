package com.homeflix.identity.account;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.homeflix.identity.client.LibraryClient;
import com.homeflix.identity.security.Caller;
import com.homeflix.identity.security.PasswordConfig;
import com.homeflix.identity.web.ForbiddenException;
import com.homeflix.identity.web.InvalidInputException;
import com.homeflix.identity.web.NotFoundException;
import com.homeflix.identity.web.ServiceUnavailableException;
import com.homeflix.identity.web.UnauthenticatedException;

/**
 * Account management (ACC-09, ACC-10, SEC-02, SEC-07): list, create, change the role, disable,
 * re-enable and delete. Deletion is also open to the account itself (K2, consistency rules).
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

	/**
	 * How many times a deletion lists the profiles again when one was created while the library
	 * was being called. Three is far more than a person can create by hand in that time.
	 */
	static final int DELETION_ROUNDS = 3;

	private final JdbcTemplate jdbc;
	private final PasswordEncoder passwordEncoder;
	private final LibraryClient library;
	private final TransactionTemplate transaction;

	public AccountManagementService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, LibraryClient library,
			PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
		this.library = library;
		this.transaction = new TransactionTemplate(transactionManager);
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

	/**
	 * An administrator deletes an account, possibly its own (SEC-07).
	 *
	 * @throws NotFoundException 404 when the account does not exist
	 * @throws LastAdministratorException 409 when it is the last enabled administrator
	 * @throws ServiceUnavailableException 503 when the library did not delete a profile's data:
	 *         the account is kept and the request can be repeated
	 */
	public void delete(Caller caller, UUID accountId) {
		deleteAccount(caller, accountId, true);
	}

	/**
	 * The signed-in account deletes itself (SEC-07). Same rules as {@link #delete}, but the caller
	 * needs no administrator role; a disabled or already deleted account gets a 401.
	 */
	public void deleteOwn(Caller caller) {
		deleteAccount(caller, caller.accountId(), false);
	}

	/**
	 * Library first, then identity (consistency rules, "Deleting an account"):
	 * <ol>
	 * <li>Under the lock, check the caller and the last-administrator rule, and list the profiles.
	 * The rule is checked before any call, so a refused deletion never empties anybody's data.</li>
	 * <li>Without a transaction, ask the library to delete each profile's data. The first failure
	 * stops everything with a 503: the account is kept, and the calls already made are harmless
	 * since they are idempotent.</li>
	 * <li>Under the lock again, check everything again and delete the account, whose profiles go
	 * with it ({@code ON DELETE CASCADE}). The account row is locked first, so no profile can be
	 * created between this check and the deletion. A profile created during step 2 has not been
	 * cleared in the library: the account is kept and step 2 runs for it.</li>
	 * </ol>
	 * No database transaction is held during the calls to the library, which can take seconds.
	 * Source media is never touched: identity has no access to it, and the library deletes only
	 * favourites and progress (K3).
	 */
	private void deleteAccount(Caller caller, UUID accountId, boolean byAdministrator) {
		Set<UUID> cleared = new HashSet<>();
		for (int round = 1; round <= DELETION_ROUNDS; round++) {
			List<UUID> profiles = transaction.execute(
					status -> lockAndCheckDeletion(caller, accountId, byAdministrator));
			for (UUID profileId : profiles) {
				if (!cleared.contains(profileId)) {
					library.deleteProfileData(profileId);
					cleared.add(profileId);
				}
			}

			Boolean deleted = transaction.execute(status -> {
				List<UUID> current = lockAndCheckDeletion(caller, accountId, byAdministrator);
				if (!cleared.containsAll(current)) {
					return false;
				}
				jdbc.update("delete from account where id = ?", accountId);
				return true;
			});
			if (Boolean.TRUE.equals(deleted)) {
				log.info("Account {} and its {} profiles deleted by {}", accountId, cleared.size(), caller.accountId());
				return;
			}
		}
		throw new ServiceUnavailableException(
				"Profiles kept being created while account " + accountId + " was being deleted.", null);
	}

	/**
	 * Takes the lock, checks that the caller may delete the account and that it is not the last
	 * enabled administrator, then locks the account row (a profile creation waits on it) and
	 * returns the ids of its profiles. Must run inside a transaction.
	 */
	private List<UUID> lockAndCheckDeletion(Caller caller, UUID accountId, boolean byAdministrator) {
		ManagedAccount account;
		if (byAdministrator) {
			lockAndCheckAdministrator(caller);
			account = find(accountId);
		}
		else {
			jdbc.queryForObject("select 1 from pg_advisory_xact_lock(?)", Integer.class, ACCOUNTS_LOCK_KEY);
			account = jdbc.query("select " + COLUMNS + " from account where id = ?", MAPPER, accountId)
					.stream().findFirst()
					.filter(found -> !found.disabled())
					.orElseThrow(UnauthenticatedException::new);
		}
		if (account.admin() && !account.disabled() && activeAdministrators() <= 1) {
			throw new LastAdministratorException();
		}
		jdbc.queryForList("select id from account where id = ? for update", UUID.class, accountId);
		return jdbc.queryForList("select id from profile where account_id = ? order by created_at, id",
				UUID.class, accountId);
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
