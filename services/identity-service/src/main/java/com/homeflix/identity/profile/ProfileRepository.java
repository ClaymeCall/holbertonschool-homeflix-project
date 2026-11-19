package com.homeflix.identity.profile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Profiles in the identity service's own database. Every query that takes a profile id also takes
 * the account id from the token, so a profile of another account is never found (PRF-04, R-06).
 */
@Repository
public class ProfileRepository {

	private static final String COLUMNS = "id, name, created_at";

	private static final RowMapper<Profile> MAPPER = (rs, rowNum) -> new Profile(
			rs.getObject("id", UUID.class),
			rs.getString("name"),
			rs.getObject("created_at", OffsetDateTime.class).toInstant());

	private final JdbcTemplate jdbc;

	public ProfileRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Oldest first; the id breaks ties. */
	public List<Profile> list(UUID accountId) {
		return jdbc.query("select " + COLUMNS + " from profile where account_id = ? order by created_at, id",
				MAPPER, accountId);
	}

	public Optional<Profile> find(UUID profileId, UUID accountId) {
		return jdbc.query("select " + COLUMNS + " from profile where id = ? and account_id = ?",
				MAPPER, profileId, accountId).stream().findFirst();
	}

	public boolean belongsTo(UUID profileId, UUID accountId) {
		return find(profileId, accountId).isPresent();
	}

	/**
	 * Locks the account row until the transaction ends, so two creations for the same account
	 * cannot both pass the limit check. False when the account no longer exists.
	 */
	public boolean lockAccount(UUID accountId) {
		return !jdbc.queryForList("select id from account where id = ? for update", UUID.class, accountId).isEmpty();
	}

	public int count(UUID accountId) {
		Integer count = jdbc.queryForObject("select count(*) from profile where account_id = ?", Integer.class,
				accountId);
		return count == null ? 0 : count;
	}

	/** @throws org.springframework.dao.DuplicateKeyException when the name is taken in this account */
	public Profile insert(UUID accountId, String name) {
		return jdbc.queryForObject("insert into profile (account_id, name) values (?, ?) returning " + COLUMNS,
				MAPPER, accountId, name);
	}

	/** @throws org.springframework.dao.DuplicateKeyException when the name is taken in this account */
	public Optional<Profile> rename(UUID profileId, UUID accountId, String name) {
		return jdbc.query("update profile set name = ? where id = ? and account_id = ? returning " + COLUMNS,
				MAPPER, name, profileId, accountId).stream().findFirst();
	}

	public void delete(UUID profileId, UUID accountId) {
		jdbc.update("delete from profile where id = ? and account_id = ?", profileId, accountId);
	}

}
