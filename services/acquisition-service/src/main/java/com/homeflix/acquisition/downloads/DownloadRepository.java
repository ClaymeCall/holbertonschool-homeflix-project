package com.homeflix.acquisition.downloads;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class DownloadRepository {

	/** A job with what the service needs to act on it. */
	record Job(UUID id, UUID entryId, String clientHash, DownloadState state) {
	}

	private static final String COLUMNS =
			"id, entry_id, state, progress_percent, error_code, created_at, finished_at";

	private static final RowMapper<Download> DOWNLOAD = (rs, row) -> new Download(
			rs.getObject("id", UUID.class),
			rs.getObject("entry_id", UUID.class),
			DownloadState.valueOf(rs.getString("state")),
			rs.getInt("progress_percent"),
			rs.getString("error_code"),
			rs.getObject("created_at", OffsetDateTime.class),
			rs.getObject("finished_at", OffsetDateTime.class));

	private final JdbcTemplate jdbc;

	DownloadRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	Optional<Download> find(UUID id) {
		return jdbc.query("select " + COLUMNS + " from download_job where id = ?", DOWNLOAD, id).stream().findFirst();
	}

	boolean hasActiveJob(UUID entryId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists (select 1 from download_job where entry_id = ? and state in ('QUEUED', 'DOWNLOADING'))",
				Boolean.class, entryId));
	}

	/**
	 * Locks the entry until the end of the transaction. A start takes it to create its job, and the cleanup
	 * of a failed or cancelled start takes it to check that no other job uses the torrent.
	 */
	void lockEntry(UUID entryId) {
		jdbc.queryForList("select id from acquisition_entry where id = ? for update", entryId);
	}

	/** Whether the entry has a job created after this one: a later attempt, which the library follows. */
	boolean hasNewerJob(UUID jobId, UUID entryId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from download_job newer
				    where newer.entry_id = ? and newer.created_at > (select created_at from download_job where id = ?))
				""", Boolean.class, entryId, jobId));
	}

	/** Whether a job other than this one is active with this info hash: the torrent is then its own. */
	boolean hasOtherActiveJob(String clientHash, UUID jobId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from download_job
				    where client_hash = ? and id <> ? and state in ('QUEUED', 'DOWNLOADING'))
				""", Boolean.class, clientHash, jobId));
	}

	/**
	 * A new job, QUEUED, with the info hash of the entry's magnet link: the torrent can be found or removed by
	 * it from the start, even if adding it fails or is interrupted. The library was told just before, so it
	 * is in sync. The unique indexes refuse a second active job for the same entry (DuplicateKeyException).
	 */
	UUID insertQueued(UUID entryId, String infoHash, UUID startedBy) {
		return jdbc.queryForObject("""
				insert into download_job (entry_id, client_hash, state, started_by, library_synced_at)
				values (?, ?, 'QUEUED', ?, now()) returning id
				""", UUID.class, entryId, infoHash, startedBy);
	}

	/** Locks the job until the end of the transaction, so a cancel and a status update cannot cross. */
	Optional<Job> lock(UUID id) {
		return jdbc.query("select id, entry_id, client_hash, state from download_job where id = ? for update",
				(rs, row) -> new Job(rs.getObject("id", UUID.class), rs.getObject("entry_id", UUID.class),
						rs.getString("client_hash"), DownloadState.valueOf(rs.getString("state"))),
				id).stream().findFirst();
	}

	/**
	 * FAILED with its reason, if the job is still active. The library is not told yet: library_synced_at is
	 * cleared.
	 *
	 * @return false when the job had already finished (a cancel came first): nothing was changed
	 */
	boolean markFailed(UUID id, String errorCode) {
		return jdbc.update("""
				update download_job set state = 'FAILED', error_code = ?, finished_at = now(), library_synced_at = null
				where id = ? and state in ('QUEUED', 'DOWNLOADING')
				""", errorCode, id) == 1;
	}

	void markLibrarySynced(UUID id) {
		jdbc.update("update download_job set library_synced_at = now() where id = ?", id);
	}

}
