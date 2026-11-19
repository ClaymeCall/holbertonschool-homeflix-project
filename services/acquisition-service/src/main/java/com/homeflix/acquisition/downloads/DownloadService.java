package com.homeflix.acquisition.downloads;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.homeflix.acquisition.catalogue.DownloadableEntry;
import com.homeflix.acquisition.catalogue.EntryRepository;
import com.homeflix.acquisition.library.LibraryClient;
import com.homeflix.acquisition.torrent.QbittorrentClient;
import com.homeflix.acquisition.web.NotFoundException;
import com.homeflix.acquisition.web.RejectedRequestException;

/**
 * Starts and cancels downloads of verified catalogue entries (ACQ-03, ACQ-05, ACQ-09), in the order the
 * consistency rules set (docs/architecture/consistency-rules.md, "Acquisition handoff"):
 *
 * <ul>
 * <li><b>Start:</b> no active job for the entry, then the library is told (it shows the title as
 * downloading), then the job is created QUEUED with the entry's info hash and the torrent added. If the
 * library does not answer, nothing is created anywhere. If adding the torrent fails in any way, the torrent
 * is removed in case the client accepted it, the job is FAILED and the library is told.</li>
 * <li><b>Cancel:</b> the torrent and its partial data are removed from the client, the job becomes FAILED
 * with {@code cancelled}, and the library is told. If the library cannot be told, the job stays marked as
 * not synced, to be told later (J6).</li>
 * </ul>
 *
 * <p>A cancel can arrive while a start is adding the torrent. The cancel holds the job's lock while it
 * removes the torrent, and the start checks the job under the same lock once the torrent is added: if the
 * job was cancelled meanwhile, the start removes the torrent itself. Either way no torrent is left running
 * for a finished job.
 *
 * <p>A crash between the job's creation and the torrent's addition leaves a QUEUED job whose torrent is not
 * in the client. J6 reconciles it by its info hash.
 *
 * The caller's permission is checked before the controller is called ({@code @AdminOnly}).
 */
@Service
public class DownloadService {

	static final String CANCELLED = "cancelled";
	static final String CLIENT_ERROR = "client_error";

	private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

	private final EntryRepository entries;
	private final DownloadRepository downloads;
	private final LibraryClient library;
	private final QbittorrentClient torrents;
	private final TransactionTemplate transaction;

	DownloadService(EntryRepository entries, DownloadRepository downloads, LibraryClient library,
			QbittorrentClient torrents, PlatformTransactionManager transactionManager) {
		this.entries = entries;
		this.downloads = downloads;
		this.library = library;
		this.torrents = torrents;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public Download start(UUID entryId, UUID startedBy) {
		DownloadableEntry entry = entries.findDownloadable(entryId).orElseThrow(NotFoundException::new);
		if (downloads.hasActiveJob(entryId)) {
			throw inProgress();
		}

		// The library first, so the catalogue never misses a download that is running.
		library.downloadStarting(entry.id(), entry.title());

		UUID jobId;
		try {
			jobId = downloads.insertQueued(entry.id(), entry.infoHash(), startedBy);
		}
		catch (DuplicateKeyException ex) {
			// Another start of the same entry won the race. The library call above changed nothing (idempotent).
			throw inProgress();
		}

		try {
			torrents.add(entry.downloadSource(), entry.infoHash(), "homeflix-job-" + jobId);
		}
		catch (RuntimeException ex) {
			failStart(jobId, entry, ex);
			throw ex;
		}
		removeTorrentIfCancelledMeanwhile(jobId);
		log.info("Download {} of entry {} started by account {}", jobId, entry.id(), startedBy);
		return get(jobId);
	}

	public Download cancel(UUID jobId) {
		UUID entryId = transaction.execute(status -> {
			DownloadRepository.Job job = downloads.lock(jobId).orElseThrow(NotFoundException::new);
			if (!job.state().active()) {
				throw new RejectedRequestException(HttpStatus.CONFLICT, "download_not_cancellable",
						"This download has already finished and cannot be cancelled.");
			}
			// Under the job's lock, so a start that is adding this torrent sees the cancel once it is done.
			// If the client is down, this throws and the job stays as it was.
			torrents.delete(job.clientHash());
			downloads.markFailed(jobId, CANCELLED);
			return job.entryId();
		});
		log.info("Download {} cancelled", jobId);
		tellLibraryItFailed(jobId, entryId);
		return get(jobId);
	}

	public Download get(UUID jobId) {
		return downloads.find(jobId).orElseThrow(NotFoundException::new);
	}

	/**
	 * Adding the torrent failed. The client may still have accepted it (a read timeout after the request
	 * arrived), so it is removed by its info hash before the job is marked FAILED. A failure of this cleanup
	 * is kept with the first error, which is the one the administrator gets.
	 */
	private void failStart(UUID jobId, DownloadableEntry entry, RuntimeException cause) {
		try {
			torrents.delete(entry.infoHash());
		}
		catch (RuntimeException ex) {
			cause.addSuppressed(ex);
		}
		try {
			if (downloads.markFailed(jobId, CLIENT_ERROR)) {
				tellLibraryItFailed(jobId, entry.id());
			}
		}
		catch (RuntimeException ex) {
			cause.addSuppressed(ex);
		}
	}

	private void removeTorrentIfCancelledMeanwhile(UUID jobId) {
		transaction.executeWithoutResult(status -> {
			DownloadRepository.Job job = downloads.lock(jobId).orElseThrow(NotFoundException::new);
			if (job.state().active()) {
				return;
			}
			try {
				torrents.delete(job.clientHash());
				log.info("Download {} was cancelled while its torrent was added: torrent removed", jobId);
			}
			catch (RuntimeException ex) {
				// The job is already cancelled and the library told. J6 removes the torrent by its hash.
				log.warn("Download {} was cancelled while its torrent was added, and the torrent could not be "
						+ "removed yet", jobId, ex);
			}
		});
	}

	private void tellLibraryItFailed(UUID jobId, UUID entryId) {
		if (library.downloadFailed(entryId)) {
			downloads.markLibrarySynced(jobId);
		}
	}

	private static RejectedRequestException inProgress() {
		return new RejectedRequestException(HttpStatus.CONFLICT, "download_in_progress",
				"A download of this title is already in progress.");
	}

}
