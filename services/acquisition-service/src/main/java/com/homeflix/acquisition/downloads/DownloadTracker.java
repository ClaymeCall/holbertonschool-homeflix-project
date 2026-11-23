package com.homeflix.acquisition.downloads;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.homeflix.acquisition.config.TrackingProperties;
import com.homeflix.acquisition.library.LibraryClient;
import com.homeflix.acquisition.torrent.QbittorrentClient;
import com.homeflix.acquisition.torrent.QbittorrentClient.Torrent;
import com.homeflix.acquisition.web.ServiceUnavailableException;

/**
 * The status check of downloads (J6; ACQ-03, ACQ-04; docs/architecture/consistency-rules.md, "Acquisition
 * handoff"), run every 10 seconds and at start-up, so the state is rebuilt from the database and the client
 * after a restart. Each check:
 *
 * <ol>
 * <li>Reads every torrent of the client, then the active jobs, in that order: a torrent seen in the list was
 * added after its job was created, so its job is always among the active jobs read next.</li>
 * <li>Gives each active job the state and progress of its torrent ({@link StateMapping}). A torrent in error
 * fails the job with {@code client_error} and is removed. A job whose torrent is not in the client after the
 * grace period fails with {@code torrent_missing}.</li>
 * <li>Removes the torrents HomeFlix added for jobs that have failed or were cancelled, if one is still in the
 * client (a cancel that came while the start was adding it, and the client was down). Torrents of
 * completed jobs and torrents HomeFlix did not add are never touched.</li>
 * <li>Tells the library about every failure it has not been told about yet, unless the entry has a newer job
 * since: the library then follows that job.</li>
 * </ol>
 *
 * A torrent client that does not answer changes no job: the check logs it and tries again next time. Any
 * other error on one job is logged and leaves the other jobs to be checked.
 */
@Component
public class DownloadTracker {

	static final String TAG_PREFIX = "homeflix-job-";

	private static final Logger log = LoggerFactory.getLogger(DownloadTracker.class);

	private final DownloadRepository downloads;
	private final QbittorrentClient torrents;
	private final LibraryClient library;
	private final TrackingProperties settings;

	DownloadTracker(DownloadRepository downloads, QbittorrentClient torrents, LibraryClient library,
			TrackingProperties settings) {
		this.downloads = downloads;
		this.torrents = torrents;
		this.library = library;
		this.settings = settings;
	}

	public void check() {
		try {
			checkTorrents();
		}
		catch (ServiceUnavailableException ex) {
			// Already logged by the client. No job changes while the client cannot be read.
			log.warn("Download status not updated: the torrent client is unavailable");
		}
		tellLibraryAboutFailures();
	}

	private void checkTorrents() {
		List<Torrent> listed = torrents.list();
		List<DownloadRepository.TrackedJob> active = downloads.activeJobs(settings.missingGrace());

		Map<String, Torrent> byHash = new HashMap<>();
		for (Torrent torrent : listed) {
			byHash.put(torrent.hash().toLowerCase(Locale.ROOT), torrent);
		}
		Set<String> activeHashes = new HashSet<>();
		for (DownloadRepository.TrackedJob job : active) {
			activeHashes.add(job.clientHash());
			try {
				track(job, byHash.get(job.clientHash()));
			}
			catch (ServiceUnavailableException ex) {
				throw ex;
			}
			catch (RuntimeException ex) {
				// One job that cannot be updated does not stop the check of the others.
				log.error("Download {} could not be checked; it is checked again next time", job.id(), ex);
			}
		}
		removeTorrentsOfFailedJobs(listed, activeHashes);
	}

	private void track(DownloadRepository.TrackedJob job, Torrent torrent) {
		if (torrent == null) {
			if (job.pastGrace()) {
				log.warn("Download {} failed: its torrent {} is no longer in the torrent client", job.id(),
						job.clientHash());
				fail(job.id(), FailureMessages.TORRENT_MISSING);
			}
			return;
		}
		Optional<StateMapping.Status> status = StateMapping.map(torrent.state(), torrent.progress());
		if (status.isEmpty()) {
			log.warn("Download {}: unknown torrent client state {}, left as it is", job.id(), torrent.state());
			return;
		}
		if (status.get().state() == DownloadState.FAILED) {
			log.warn("Download {} failed: the torrent client reports the state {} for {}", job.id(),
					torrent.state(), job.clientHash());
			if (fail(job.id(), DownloadService.CLIENT_ERROR)) {
				removeQuietly(job.clientHash());
			}
			return;
		}
		downloads.updateStatus(job.id(), status.get().state(), status.get().progressPercent());
	}

	/** FAILED if still active. The library is told at the end of the check. */
	private boolean fail(UUID jobId, String errorCode) {
		return downloads.markFailed(jobId, errorCode);
	}

	private void removeTorrentsOfFailedJobs(List<Torrent> listed, Set<String> activeHashes) {
		Set<String> ours = new HashSet<>();
		for (Torrent torrent : listed) {
			String hash = torrent.hash().toLowerCase(Locale.ROOT);
			// The tag only says HomeFlix added the torrent. Which job owns it is found by its hash, since a
			// new start of the same entry keeps a torrent left in the client, with the old job's tag.
			if (!activeHashes.contains(hash) && jobOf(torrent).isPresent()) {
				ours.add(hash);
			}
		}
		for (String hash : downloads.latestJobFailedAmong(ours)) {
			// A new start may have taken this torrent over since the list was read: it is then kept.
			if (downloads.hasActiveJobWithHash(hash)) {
				continue;
			}
			log.info("Removing the torrent {} of a failed download", hash);
			removeQuietly(hash);
		}
	}

	/** The job HomeFlix added this torrent for, from its {@code homeflix-job-<id>} tag. */
	static Optional<UUID> jobOf(Torrent torrent) {
		if (torrent.tags() == null) {
			return Optional.empty();
		}
		for (String tag : torrent.tags().split(",")) {
			String name = tag.strip();
			if (name.startsWith(TAG_PREFIX)) {
				try {
					return Optional.of(UUID.fromString(name.substring(TAG_PREFIX.length())));
				}
				catch (IllegalArgumentException ex) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	private void removeQuietly(String hash) {
		try {
			torrents.delete(hash);
		}
		catch (ServiceUnavailableException ex) {
			// Already logged by the client. Tried again at the next check.
		}
	}

	private void tellLibraryAboutFailures() {
		downloads.markSupersededFailuresSynced();
		for (DownloadRepository.UnsyncedFailure failure : downloads.unsyncedFailures()) {
			if (library.downloadFailed(failure.entryId())) {
				downloads.markLibrarySynced(failure.id());
			}
		}
	}

}
