package com.homeflix.library.roots;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class RootRepository {

	private static final RowMapper<Root> ROOT = (rs, row) -> new Root(rs.getObject("id", UUID.class),
			rs.getString("path"), rs.getObject("created_at", java.time.OffsetDateTime.class));

	private final JdbcTemplate jdbc;

	RootRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<Root> findAll() {
		return jdbc.query("select id, path, created_at from media_root order by created_at, path", ROOT);
	}

	Optional<Root> find(UUID id) {
		return jdbc.query("select id, path, created_at from media_root where id = ?", ROOT, id).stream().findFirst();
	}

	Root insert(String path) {
		return jdbc.queryForObject(
				"insert into media_root (path) values (?) returning id, path, created_at", ROOT, path);
	}

	/** Serializes changes to the list of roots, so two overlapping folders cannot be added at once. */
	void lock() {
		jdbc.queryForObject("select pg_advisory_xact_lock(hashtext('media_root'))::text", String.class);
	}

	/** Marks the titles backed by a file of this root as unavailable, before the root goes (LIB-07). */
	int markTitlesUnavailable(UUID rootId) {
		return jdbc.update("""
				update media_item set availability = 'UNAVAILABLE'
				where availability = 'AVAILABLE'
				  and media_file_id in (select id from media_file where root_id = ?)
				""", rootId);
	}

	void delete(UUID id) {
		jdbc.update("delete from media_root where id = ?", id);
	}

}
