package com.homeflix.library.favourites;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Each query is for one profile, so a profile can only reach its own list (PRF-03, PRF-04). */
@Repository
class FavouriteRepository {

	private final JdbcTemplate jdbc;

	FavouriteRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	boolean titleExists(UUID mediaId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from media_item where id = ?)",
				Boolean.class, mediaId));
	}

	/** Idempotent: a title already in the list keeps its place and its date. */
	void add(UUID profileId, UUID mediaId) {
		jdbc.update("insert into favourite (profile_id, media_item_id) values (?, ?) on conflict do nothing", profileId,
				mediaId);
	}

	void remove(UUID profileId, UUID mediaId) {
		jdbc.update("delete from favourite where profile_id = ? and media_item_id = ?", profileId, mediaId);
	}

	/** The titles of the list, most recently added first. */
	List<UUID> ids(UUID profileId) {
		return jdbc.queryForList("select media_item_id from favourite where profile_id = ? order by added_at desc, media_item_id",
				UUID.class, profileId);
	}

}
