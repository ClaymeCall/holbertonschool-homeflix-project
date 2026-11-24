package com.homeflix.library.downloads;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DownloadRepository {

	/** The catalogue entry of a download. {@code fileId} is null until the file is linked. */
	record Item(UUID id, String availability, UUID fileId) {
	}

	record FileRow(UUID id, UUID rootId, String relativePath) {
	}

	private final JdbcTemplate jdbc;

	DownloadRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The entry of this download, locked until the end of the transaction so two calls cannot cross. */
	Optional<Item> lockItem(UUID entryId) {
		return jdbc.query("select id, availability, media_file_id from media_item where acquisition_entry_id = ? for update",
				(rs, row) -> new Item(rs.getObject("id", UUID.class), rs.getString("availability"),
						rs.getObject("media_file_id", UUID.class)), entryId).stream().findFirst();
	}

	/** Creates the entry as downloading. The entry id is unique, so a concurrent call creates nothing twice. */
	void insertDownloading(UUID entryId, String title) {
		jdbc.update("""
				insert into media_item (acquisition_entry_id, title, availability) values (?, ?, 'DOWNLOADING')
				on conflict (acquisition_entry_id) do nothing
				""", entryId, title);
	}

	void setAvailability(UUID itemId, String availability) {
		jdbc.update("update media_item set availability = ? where id = ?", availability, itemId);
	}

	Optional<FileRow> findFile(UUID fileId) {
		return jdbc.query("select id, root_id, relative_path from media_file where id = ?", (rs, row) -> new FileRow(
				rs.getObject("id", UUID.class), rs.getObject("root_id", UUID.class), rs.getString("relative_path")),
				fileId).stream().findFirst();
	}

	Optional<UUID> findFileId(UUID rootId, String relativePath) {
		return jdbc.query("select id from media_file where root_id = ? and relative_path = ?",
				(rs, row) -> rs.getObject("id", UUID.class), rootId, relativePath).stream().findFirst();
	}

	UUID insertFile(UUID rootId, String relativePath, long sizeBytes, OffsetDateTime seenAt) {
		return jdbc.queryForObject("""
				insert into media_file (root_id, relative_path, size_bytes, last_seen_at)
				values (?, ?, ?, ?) returning id
				""", UUID.class, rootId, relativePath, sizeBytes, seenAt);
	}

	void markSeen(UUID fileId, long sizeBytes, OffsetDateTime seenAt) {
		jdbc.update("update media_file set size_bytes = ?, last_seen_at = ?, missing_since = null where id = ?",
				sizeBytes, seenAt, fileId);
	}

	/**
	 * Removes the entry a scan created for this file before the download was handed over. Only an entry that
	 * is not itself a download is removed. It was just created, so it holds no favourite or progress worth
	 * keeping (consistency rules, "A scan may find the file first").
	 */
	int deleteScanEntryOf(UUID fileId) {
		return jdbc.update("delete from media_item where media_file_id = ? and acquisition_entry_id is null", fileId);
	}

	void linkFile(UUID itemId, UUID fileId) {
		jdbc.update("update media_item set media_file_id = ?, availability = 'AVAILABLE' where id = ?", fileId, itemId);
	}

}
