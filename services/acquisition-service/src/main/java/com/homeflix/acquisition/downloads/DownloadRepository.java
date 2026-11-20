package com.homeflix.acquisition.downloads;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class DownloadRepository {

	/** A job with what the service needs to act on it. */
	record Job(UUID id, UUID entryId, String clientHash, DownloadState state, String completedRelativePath) {
	}

	/**
	 * An active job, for the status check: {@code pastGrace} once it is older than the missing-torrent grace
	 * period, its entry's expected size, the verified file once there is one, and {@code handoffExpired} once
	 * the handoff has waited longer than its time limit.
	 */
	record TrackedJob(UUID id, UUID entryId, String clientHash, boolean pastGrace, long expectedSizeBytes,
			String completedRelativePath, boolean handoffExpired) {
	}

	/** A failed job whose failure the library has not been told about yet. */
	record UnsyncedFailure(UUID id, UUID entryId) {
	}

	private static final String COLUMNS =
			"id, entry_id, state, progress_percent, error_code, created_at, finished_at";

	private static final RowMapper<Download> DOWNLOAD = (rs, row) -> new Download(
			rs.getObject("id", UUID.class),
			rs.getObject("entry_id", UUID.class),
			DownloadState.valueOf(rs.getString("state")),
			rs.getInt("progress_percent"),
			rs.getString("error_code"),
			FailureMessages.forCode(rs.getString("error_code")),
			rs.getObject("created_at", OffsetDateTime.class),
			rs.getObject("finished_at", OffsetDateTime.class));

	private final JdbcTemplate jdbc;

	DownloadRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	Optional<Download> find(UUID id) {
		return jdbc.query("select " + COLUMNS + " from download_job where id = ?", DOWNLOAD, id).stream().findFirst();
	}

	/** Every download, newest first. */
	List<Download> findAll() {
		return jdbc.query("select " + COLUMNS + " from download_job order by created_at desc, id", DOWNLOAD);
	}

	List<TrackedJob> activeJobs(Duration grace, Duration handoffTimeout) {
		return jdbc.query("""
				select j.id, j.entry_id, j.client_hash, j.completed_relative_path, e.expected_size_bytes,
				    j.created_at < now() - make_interval(secs => ?) as past_grace,
				    coalesce(j.handoff_started_at < now() - make_interval(secs => ?), false) as handoff_expired
				from download_job j join acquisition_entry e on e.id = j.entry_id
				where j.state in ('QUEUED', 'DOWNLOADING')
				""",
				(rs, row) -> new TrackedJob(rs.getObject("id", UUID.class), rs.getObject("entry_id", UUID.class),
						rs.getString("client_hash"), rs.getBoolean("past_grace"), rs.getLong("expected_size_bytes"),
						rs.getString("completed_relative_path"), rs.getBoolean("handoff_expired")),
				grace.toMillis() / 1000.0, handoffTimeout.toMillis() / 1000.0);
	}

	/** The file passed the checks: the handoff starts now, and its time limit runs from here. */
	void markVerified(UUID id, String relativePath) {
		jdbc.update("""
				update download_job set completed_relative_path = ?, handoff_started_at = now()
				where id = ? and state in ('QUEUED', 'DOWNLOADING') and completed_relative_path is null
				""", relativePath, id);
	}

	/** The library has the file: COMPLETED, and the library is in sync. */
	boolean markCompleted(UUID id) {
		return jdbc.update("""
				update download_job set state = 'COMPLETED', progress_percent = 100, finished_at = now(),
				    library_synced_at = now()
				where id = ? and state in ('QUEUED', 'DOWNLOADING') and completed_relative_path is not null
				""", id) == 1;
	}

	/**
	 * The state and progress the torrent client reports, if the job is still active and they changed. A job
	 * cancelled in the meantime is left as it is.
	 */
	void updateStatus(UUID id, DownloadState state, int progressPercent) {
		jdbc.update("""
				update download_job set state = ?, progress_percent = ?
				where id = ? and state in ('QUEUED', 'DOWNLOADING') and (state <> ? or progress_percent <> ?)
				""", state.name(), progressPercent, id, state.name(), progressPercent);
	}

	List<UnsyncedFailure> unsyncedFailures() {
		return jdbc.query("select id, entry_id from download_job where state = 'FAILED' and library_synced_at is null",
				(rs, row) -> new UnsyncedFailure(rs.getObject("id", UUID.class), rs.getObject("entry_id", UUID.class)));
	}

	/**
	 * Those of these jobs that are FAILED, except a failed handoff: its file passed the checks and is kept, so
	 * starting the download again hands it over without downloading it again.
	 */
	Set<UUID> failedAmong(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
		return Set.copyOf(jdbc.queryForList("select id from download_job where state = 'FAILED' "
				+ "and error_code <> 'handoff_failed' and id in (" + placeholders + ")",
				UUID.class, ids.toArray()));
	}

	boolean hasActiveJobWithHash(String clientHash) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists (select 1 from download_job where client_hash = ? and state in ('QUEUED', 'DOWNLOADING'))",
				Boolean.class, clientHash));
	}

	boolean hasActiveJob(UUID entryId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists (select 1 from download_job where entry_id = ? and state in ('QUEUED', 'DOWNLOADING'))",
				Boolean.class, entryId));
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
		return jdbc.query("""
				select id, entry_id, client_hash, state, completed_relative_path from download_job where id = ? for update
				""",
				(rs, row) -> new Job(rs.getObject("id", UUID.class), rs.getObject("entry_id", UUID.class),
						rs.getString("client_hash"), DownloadState.valueOf(rs.getString("state")),
						rs.getString("completed_relative_path")),
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
