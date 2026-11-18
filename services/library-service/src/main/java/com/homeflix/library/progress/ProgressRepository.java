package com.homeflix.library.progress;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Viewing progress. Every query is for one profile, so a profile can only reach its own rows (PRF-03,
 * PRF-04). Values are bound parameters (SEC-01).
 */
@Repository
class ProgressRepository {

	private final JdbcTemplate jdbc;

	ProgressRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	boolean titleExists(UUID mediaId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from media_item where id = ?)",
				Boolean.class, mediaId));
	}

	/** Creates the entry or replaces it: one row for each profile and title. */
	ProgressEntry save(UUID profileId, UUID mediaId, int position, int duration) {
		return jdbc.queryForObject("""
				insert into progress (profile_id, media_item_id, position_seconds, duration_seconds)
				values (?, ?, ?, ?)
				on conflict (profile_id, media_item_id)
				do update set position_seconds = excluded.position_seconds,
				              duration_seconds = excluded.duration_seconds
				returning media_item_id, position_seconds, duration_seconds, updated_at
				""", ProgressRepository::entry, profileId, mediaId, position, duration);
	}

	Optional<ProgressEntry> find(UUID profileId, UUID mediaId) {
		return jdbc.query("""
				select media_item_id, position_seconds, duration_seconds, updated_at
				from progress where profile_id = ? and media_item_id = ?
				""", ProgressRepository::entry, profileId, mediaId).stream().findFirst();
	}

	void delete(UUID profileId, UUID mediaId) {
		jdbc.update("delete from progress where profile_id = ? and media_item_id = ?", profileId, mediaId);
	}

	/** Most recently watched first. {@code state} null means every entry. */
	List<ProgressEntry> list(UUID profileId, ProgressState state, int limit, int offset) {
		StringBuilder sql = new StringBuilder("""
				select media_item_id, position_seconds, duration_seconds, updated_at
				from progress where profile_id = ?
				""");
		if (state == ProgressState.COMPLETED) {
			sql.append(" and position_seconds * 10 >= duration_seconds * 9");
		}
		else if (state == ProgressState.STARTED) {
			sql.append(" and position_seconds * 10 < duration_seconds * 9 and position_seconds > ")
					.append(ProgressState.MIN_SECONDS);
		}
		sql.append(" order by updated_at desc, media_item_id limit ? offset ?");
		return jdbc.query(sql.toString(), ProgressRepository::entry, profileId, limit, offset);
	}

	private static ProgressEntry entry(ResultSet rs, int row) throws SQLException {
		int position = rs.getInt("position_seconds");
		int duration = rs.getInt("duration_seconds");
		return new ProgressEntry(rs.getObject("media_item_id", UUID.class), position, duration,
				ProgressState.of(position, duration), rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant(),
				null);
	}

}
