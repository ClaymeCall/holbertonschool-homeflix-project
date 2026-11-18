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
import com.homeflix.acquisition.web.ServiceUnavailableException;

/**
 * Starts and cancels downloads of verified catalogue entries (ACQ-03, ACQ-05, ACQ-09), in the order the
 * consistency rules set (docs/architecture/consistency-rules.md, "Acquisition handoff"):
 *
 * <ul>
 * <li><b>Start:</b> no active job for the entry, then the library is told (it shows the title as
 * downloading), then the job is created QUEUED and the torrent added. If the library does not answer,
 * nothing is created anywhere. If the torrent client fails, the job is FAILED and the library is told.</li>
 * <li><b>Cancel:</b> the torrent and its partial data are removed from the client, the job becomes FAILED
 * with {@code cancelled}, and the library is told. If the library cannot be told, the job stays marked as
 * not synced, to be told later (J6).</li>
 * </ul>
 *
 * The caller's permission is checked by the controller.
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
			jobId = downloads.insertQueued(entry.id(), startedBy);
		}
		catch (DuplicateKeyException ex) {
			// Another start of the same entry won the race. The library call above changed nothing (idempotent).
			throw inProgress();
		}

		try {
			String hash = torrents.add(entry.downloadSource(), "homeflix-job-" + jobId).hash();
			downloads.setClientHash(jobId, hash);
		}
		catch (ServiceUnavailableException ex) {
			downloads.markFailed(jobId, CLIENT_ERROR);
			tellLibraryItFailed(jobId, entry.id());
			throw ex;
		}
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
			if (job.clientHash() != null) {
				// If the client is down, this throws and the job stays as it was.
				torrents.delete(job.clientHash());
			}
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
