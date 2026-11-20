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

import com.homeflix.acquisition.config.DownloadsProperties;
import com.homeflix.acquisition.config.TrackingProperties;
import com.homeflix.acquisition.library.LibraryClient;
import com.homeflix.acquisition.torrent.QbittorrentClient;
import com.homeflix.acquisition.torrent.QbittorrentClient.Torrent;
import com.homeflix.acquisition.verification.DownloadedFileVerifier;
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
 * <li>Hands each completed torrent to the library (J7): the file is checked once
 * ({@link DownloadedFileVerifier}); a file that fails the checks fails the job with
 * {@code verification_failed} and is deleted with its torrent. A verified file is handed over at each check
 * until the library accepts it, and the job is then COMPLETED. After the handoff time limit it fails with
 * {@code handoff_failed}, and the file is kept.</li>
 * <li>Removes the torrents HomeFlix added for jobs that have failed or were cancelled, if one is still in the
 * client (a cancel that came while the start was adding it, and the client was down). Torrents of
 * completed jobs and torrents HomeFlix did not add are never touched.</li>
 * <li>Tells the library about every failure it has not been told about yet.</li>
 * </ol>
 *
 * A torrent client that does not answer changes no job: the check logs it and tries again next time.
 */
@Component
public class DownloadTracker {

	static final String TAG_PREFIX = "homeflix-job-";

	private static final Logger log = LoggerFactory.getLogger(DownloadTracker.class);

	private final DownloadRepository downloads;
	private final QbittorrentClient torrents;
	private final LibraryClient library;
	private final DownloadedFileVerifier verifier;
	private final TrackingProperties settings;
	private final DownloadsProperties downloadSettings;

	DownloadTracker(DownloadRepository downloads, QbittorrentClient torrents, LibraryClient library,
			DownloadedFileVerifier verifier, TrackingProperties settings, DownloadsProperties downloadSettings) {
		this.downloads = downloads;
		this.torrents = torrents;
		this.library = library;
		this.verifier = verifier;
		this.settings = settings;
		this.downloadSettings = downloadSettings;
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
		List<DownloadRepository.TrackedJob> active = downloads.activeJobs(settings.missingGrace(),
				downloadSettings.handoffTimeout());

		Map<String, Torrent> byHash = new HashMap<>();
		for (Torrent torrent : listed) {
			byHash.put(torrent.hash().toLowerCase(Locale.ROOT), torrent);
		}
		Set<String> activeHashes = new HashSet<>();
		for (DownloadRepository.TrackedJob job : active) {
			activeHashes.add(job.clientHash());
			track(job, byHash.get(job.clientHash()));
		}
		removeTorrentsOfFailedJobs(listed, activeHashes);
	}

	private void track(DownloadRepository.TrackedJob job, Torrent torrent) {
		if (torrent == null) {
			if (job.completedRelativePath() != null) {
				// Verified file, torrent gone from the client: the handoff only needs the file.
				handOver(job, null);
				return;
			}
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
		if (status.get().progressPercent() == 100) {
			handOver(job, torrent);
		}
	}

	/** The torrent is complete: check the file once, then hand it to the library until it accepts it. */
	private void handOver(DownloadRepository.TrackedJob job, Torrent torrent) {
		String relativePath = job.completedRelativePath();
		if (relativePath == null) {
			DownloadedFileVerifier.Result result = verifier.verify(torrent.savePath(), torrent.contentPath(),
					job.expectedSizeBytes());
			if (result instanceof DownloadedFileVerifier.Refused refused) {
				log.warn("Download {} failed the checks of the completed file: {}", job.id(), refused.reason());
				if (fail(job.id(), FailureMessages.VERIFICATION_FAILED)) {
					// Never kept where the library could index it (R-19).
					removeQuietly(job.clientHash());
				}
				return;
			}
			relativePath = ((DownloadedFileVerifier.Accepted) result).relativePath();
			downloads.markVerified(job.id(), relativePath);
			log.info("Download {} passed the checks; handing {} to the library", job.id(), relativePath);
		}
		if (library.downloadCompleted(job.entryId(), relativePath)) {
			if (downloads.markCompleted(job.id())) {
				log.info("Download {} completed and handed to the library", job.id());
			}
		}
		else if (job.handoffExpired()) {
			log.warn("Download {} failed: the library did not accept the file within {}", job.id(),
					downloadSettings.handoffTimeout());
			fail(job.id(), FailureMessages.HANDOFF_FAILED);
		}
	}

	/** FAILED if still active. The library is told at the end of the check. */
	private boolean fail(UUID jobId, String errorCode) {
		return downloads.markFailed(jobId, errorCode);
	}

	private void removeTorrentsOfFailedJobs(List<Torrent> listed, Set<String> activeHashes) {
		Map<UUID, String> hashByJob = new HashMap<>();
		for (Torrent torrent : listed) {
			String hash = torrent.hash().toLowerCase(Locale.ROOT);
			if (!activeHashes.contains(hash)) {
				jobOf(torrent).ifPresent(jobId -> hashByJob.put(jobId, hash));
			}
		}
		for (UUID jobId : downloads.failedAmong(hashByJob.keySet())) {
			String hash = hashByJob.get(jobId);
			// A new start may have taken this torrent over since the list was read: it is then kept.
			if (downloads.hasActiveJobWithHash(hash)) {
				continue;
			}
			log.info("Removing the torrent of failed download {}", jobId);
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
		for (DownloadRepository.UnsyncedFailure failure : downloads.unsyncedFailures()) {
			if (library.downloadFailed(failure.entryId())) {
				downloads.markLibrarySynced(failure.id());
			}
		}
	}

}
