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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 * {@code verification_failed} and is deleted with its torrent; a file that cannot be read is kept and
 * checked again at the next check. A verified file is handed over at each check
 * until the library accepts it, and the job is then COMPLETED. After the handoff time limit it fails with
 * {@code handoff_failed}, and the file is kept.</li>
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
	private final DownloadedFileVerifier verifier;
	private final TrackingProperties settings;
	private final DownloadsProperties downloadSettings;
	private final TransactionTemplate transaction;

	DownloadTracker(DownloadRepository downloads, QbittorrentClient torrents, LibraryClient library,
			DownloadedFileVerifier verifier, TrackingProperties settings, DownloadsProperties downloadSettings,
			PlatformTransactionManager transactionManager) {
		this.downloads = downloads;
		this.torrents = torrents;
		this.library = library;
		this.verifier = verifier;
		this.settings = settings;
		this.downloadSettings = downloadSettings;
		this.transaction = new TransactionTemplate(transactionManager);
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
			if (stillBeingMoved(torrent)) {
				// Complete, but the client has not finished moving the file into the downloads folder.
				checkAgainLater(job, "the torrent client has not moved the file to the downloads folder yet: "
						+ torrent.contentPath());
				return;
			}
			DownloadedFileVerifier.Result result = verifier.verify(torrent.contentPath(), job.expectedSizeBytes());
			if (result instanceof DownloadedFileVerifier.Unreadable unreadable) {
				checkAgainLater(job, unreadable.reason());
				return;
			}
			if (result instanceof DownloadedFileVerifier.Refused refused) {
				log.warn("Download {} failed the checks of the completed file: {}", job.id(), refused.reason());
				if (fail(job.id(), FailureMessages.VERIFICATION_FAILED)) {
					// Never kept where the library could index it (R-19).
					removeQuietly(job.clientHash());
				}
				return;
			}
			relativePath = ((DownloadedFileVerifier.Accepted) result).relativePath();
			if (!downloads.markVerified(job.id(), relativePath)) {
				// Cancelled while the file was checked: the cancel removed it, so the library is not told.
				log.info("Download {} was cancelled while its file was checked: not handed over", job.id());
				return;
			}
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

	/**
	 * The completed file cannot be checked yet (a permission or I/O problem, or a file the client is still
	 * moving): kept, and checked again next time. Past the handoff time limit the failure is made visible,
	 * still without deleting it.
	 */
	private void checkAgainLater(DownloadRepository.TrackedJob job, String reason) {
		if (downloads.markUnreadable(job.id(), downloadSettings.handoffTimeout())) {
			log.warn("Download {} failed: the completed file could not be read for {}: {}", job.id(),
					downloadSettings.handoffTimeout(), reason);
			fail(job.id(), FailureMessages.FILE_UNREADABLE);
		}
		else {
			log.warn("Download {}: the completed file cannot be read, checked again at the next check: {}",
					job.id(), reason);
		}
	}

	/**
	 * True while the client moves a complete torrent from its folder for files in progress (J4) to the
	 * downloads folder: the torrent is already at 100 %, but its file is not, or not wholly, in place.
	 */
	static boolean stillBeingMoved(Torrent torrent) {
		if (torrent == null) {
			return false;
		}
		if ("moving".equals(torrent.state())) {
			return true;
		}
		String inProgress = torrent.downloadPath();
		String content = torrent.contentPath();
		if (inProgress == null || inProgress.isBlank() || content == null) {
			return false;
		}
		return content.startsWith(inProgress.endsWith("/") ? inProgress : inProgress + "/");
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
			// Checked again under the entry's lock, which a start holds until its job exists: a new start that
			// takes this torrent over is either seen here, or adds the torrent after it is removed.
			transaction.executeWithoutResult(status -> {
				downloads.lockEntry(downloads.entryOfHash(hash));
				if (downloads.latestJobFailedAmong(Set.of(hash)).isEmpty()) {
					return;
				}
				log.info("Removing the torrent {} of a failed download", hash);
				removeQuietly(hash);
			});
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

	/**
	 * Under the entry's lock, which a start holds while it tells the library: a failure of an earlier attempt
	 * is never told after a new attempt told the library the title downloads (consistency rules).
	 */
	private void tellLibraryAboutFailures() {
		for (DownloadRepository.UnsyncedFailure failure : downloads.unsyncedFailures()) {
			transaction.executeWithoutResult(status -> {
				downloads.lockEntry(failure.entryId());
				if (downloads.hasNewerJob(failure.id(), failure.entryId())) {
					// The library follows the newer job.
					downloads.markLibrarySynced(failure.id());
				}
				else if (library.downloadFailed(failure.entryId())) {
					downloads.markLibrarySynced(failure.id());
				}
			});
		}
	}

}
