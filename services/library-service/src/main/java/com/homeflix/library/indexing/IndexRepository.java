package com.homeflix.library.indexing;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class IndexRepository {

	record FileRow(UUID id, long sizeBytes) {
	}

	private final JdbcTemplate jdbc;

	IndexRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	Optional<FileRow> findFile(UUID rootId, String relativePath) {
		return jdbc.query("select id, size_bytes from media_file where root_id = ? and relative_path = ?",
				(rs, row) -> new FileRow(rs.getObject("id", UUID.class), rs.getLong("size_bytes")), rootId, relativePath)
				.stream().findFirst();
	}

	UUID insertFile(UUID rootId, String relativePath, long sizeBytes, OffsetDateTime seenAt) {
		return jdbc.queryForObject("""
				insert into media_file (root_id, relative_path, size_bytes, last_seen_at)
				values (?, ?, ?, ?) returning id
				""", UUID.class, rootId, relativePath, sizeBytes, seenAt);
	}

	/** The file was seen again: records when, its size, and that it is no longer missing. */
	void markSeen(UUID fileId, long sizeBytes, OffsetDateTime seenAt) {
		jdbc.update("update media_file set size_bytes = ?, last_seen_at = ?, missing_since = null where id = ?",
				sizeBytes, seenAt, fileId);
	}

	/** The catalogue entry of a new file. Only the title is filled in (CAT-10). */
	void insertItem(UUID fileId, String title) {
		jdbc.update("insert into media_item (media_file_id, title, availability) values (?, ?, 'AVAILABLE')",
				fileId, title);
	}

	/** A file that was missing is back: its title becomes available again. True if one changed. */
	boolean makeAvailableAgain(UUID fileId) {
		return jdbc.update("update media_item set availability = 'AVAILABLE' where media_file_id = ? and availability = 'UNAVAILABLE'",
				fileId) > 0;
	}

	/**
	 * A whole root cannot be read: every file it holds is now missing, and its available titles become
	 * unavailable, keeping everything else they have (LIB-07). Returns how many titles changed.
	 */
	int markRootMissing(UUID rootId, OffsetDateTime now) {
		return jdbc.update("""
				with gone as (
				    update media_file set missing_since = coalesce(missing_since, ?)
				    where root_id = ?
				    returning id)
				update media_item set availability = 'UNAVAILABLE'
				where availability = 'AVAILABLE' and media_file_id in (select id from gone)
				""", now, rootId);
	}

	/**
	 * Marks the files of a root that this scan did not see, and makes their titles unavailable (LIB-07).
	 * Returns how many titles changed. A title keeps everything else it has.
	 */
	int markMissing(UUID rootId, OffsetDateTime scanStart, OffsetDateTime now) {
		return jdbc.update("""
				with gone as (
				    update media_file set missing_since = ?
				    where root_id = ? and last_seen_at < ? and missing_since is null
				    returning id)
				update media_item set availability = 'UNAVAILABLE'
				where availability = 'AVAILABLE' and media_file_id in (select id from gone)
				""", now, rootId, scanStart);
	}

}
