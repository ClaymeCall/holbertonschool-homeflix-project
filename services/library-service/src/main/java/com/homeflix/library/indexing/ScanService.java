package com.homeflix.library.indexing;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.homeflix.library.roots.Root;
import com.homeflix.library.roots.RootService;
import com.homeflix.library.roots.RootValidator;
import com.homeflix.library.web.RejectedRequestException;

/**
 * Indexes the authorized folders (LIB-04, LIB-05, LIB-06).
 *
 * <p>A file is identified by its root and its relative path, so a repeated scan finds the same rows
 * and adds nothing. A new file gets a catalogue entry titled after its file name. That entry is never
 * changed again by a scan, so an administrator's edits survive (data model, "two tables for one title").
 * A file that was found before and is gone now makes its title {@code UNAVAILABLE}, and comes back when
 * the file does (LIB-07).
 */
@Service
public class ScanService {

	private static final Logger log = LoggerFactory.getLogger(ScanService.class);

	/** The codes a root can be reported with (docs/api/library.yaml). */
	private static final Set<String> ROOT_ERRORS = Set.of("directory_not_found", "directory_not_readable", "not_a_directory");

	private static final int MAX_TITLE_LENGTH = 500;

	private final RootService roots;
	private final RootValidator rootValidator;
	private final IndexRepository index;
	private final ScanGate gate;
	private final TransactionTemplate transaction;

	ScanService(RootService roots, RootValidator rootValidator, IndexRepository index, ScanGate gate,
			PlatformTransactionManager transactionManager) {
		this.roots = roots;
		this.rootValidator = rootValidator;
		this.index = index;
		this.gate = gate;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * Runs one scan of every authorized folder and returns its report.
	 *
	 * @throws RejectedRequestException 409 {@code scan_in_progress} when another scan is running
	 */
	public ScanReport run() {
		if (!gate.tryEnter()) {
			throw new RejectedRequestException(HttpStatus.CONFLICT, "scan_in_progress",
					"A scan is already running. Try again when it has finished.");
		}
		try {
			return scanAll();
		}
		finally {
			gate.leave();
		}
	}

	private ScanReport scanAll() {
		OffsetDateTime startedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
		Counts counts = new Counts();
		List<ScanReport.Unsupported> unsupported = new ArrayList<>();
		List<ScanReport.InaccessibleRoot> inaccessible = new ArrayList<>();

		for (Root root : roots.list()) {
			RootValidator.Accepted folder;
			try {
				// Access is checked again at each scan (LIB-03).
				folder = rootValidator.validate(root.path());
			}
			catch (RejectedRequestException e) {
				String code = ROOT_ERRORS.contains(e.code()) ? e.code() : "directory_not_readable";
				log.warn("Root skipped, it cannot be scanned: {} ({})", root.path(), e.code());
				inaccessible.add(new ScanReport.InaccessibleRoot(root.id(), code));
				counts.markedUnavailable += markUnavailable(root);
				continue;
			}
			try {
				scanRoot(root, folder, startedAt, counts, unsupported);
			}
			catch (IOException e) {
				// One root failing must not stop the others, nor crash the request (LIB-07, H6).
				log.error("Root could not be read: {}", root.path(), e);
				inaccessible.add(new ScanReport.InaccessibleRoot(root.id(), "directory_not_readable"));
			}
			catch (RuntimeException e) {
				// Not a file problem (typically the database): the folder is fine, so do not blame it (#190).
				log.error("Root scan failed: {}", root.path(), e);
				inaccessible.add(new ScanReport.InaccessibleRoot(root.id(), "scan_failed"));
			}
		}
		OffsetDateTime finishedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
		log.info("Scan finished: {} added, {} seen again, {} marked unavailable, {} unsupported, {} roots not readable",
				counts.added, counts.updated, counts.markedUnavailable, unsupported.size(), inaccessible.size());
		return new ScanReport(startedAt, finishedAt, counts.added, counts.updated, counts.markedUnavailable,
				unsupported, inaccessible);
	}

	/**
	 * A root that cannot be read has no file we can see, so its titles are unavailable until it can be read
	 * again (LIB-07, AS-09). The titles keep their information, favourites and progress. Never throws: a
	 * failure here is logged and the scan goes on with the other roots.
	 */
	private int markUnavailable(Root root) {
		try {
			Integer changed = transaction.execute(status -> index.markRootMissing(root.id(),
					OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)));
			return changed == null ? 0 : changed;
		}
		catch (RuntimeException e) {
			log.error("Titles of an unreadable root could not be marked unavailable: {}", root.path(), e);
			return 0;
		}
	}

	private void scanRoot(Root root, RootValidator.Accepted folder, OffsetDateTime startedAt, Counts counts,
			List<ScanReport.Unsupported> unsupported) throws IOException {
		FoundFiles found = FoundFiles.walk(folder.realPath());
		for (String relativePath : found.unsupported()) {
			unsupported.add(new ScanReport.Unsupported(root.id(), relativePath, MediaFileKind.UNSUPPORTED_VIDEO.errorCode()));
		}
		Counts rootCounts = transaction.execute(status -> {
			Counts local = new Counts();
			for (FoundFiles.Video video : found.supported()) {
				record(root, video, startedAt, local);
			}
			if (found.complete()) {
				local.markedUnavailable = index.markMissing(root.id(), startedAt,
						OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));
			}
			else {
				log.warn("Part of {} could not be read, so no title is marked unavailable for it in this scan", root.path());
			}
			return local;
		});
		counts.add(rootCounts);
	}

	private void record(Root root, FoundFiles.Video video, OffsetDateTime seenAt, Counts counts) {
		var known = index.findFile(root.id(), video.relativePath());
		if (known.isEmpty()) {
			var fileId = index.insertFile(root.id(), video.relativePath(), video.sizeBytes(), seenAt);
			index.insertItem(fileId, titleOf(video.relativePath()));
			counts.added++;
			return;
		}
		index.markSeen(known.get().id(), video.sizeBytes(), seenAt);
		index.makeAvailableAgain(known.get().id());
		counts.updated++;
	}

	/** The file name without its extension: {@code Films/The Great Escape.mp4} gives {@code The Great Escape}. */
	static String titleOf(String relativePath) {
		String name = relativePath.substring(relativePath.lastIndexOf('/') + 1);
		int dot = name.lastIndexOf('.');
		String title = (dot > 0 ? name.substring(0, dot) : name).trim();
		if (title.isEmpty()) {
			title = name;
		}
		return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
	}

	private static final class Counts {

		int added;
		int updated;
		int markedUnavailable;

		void add(Counts other) {
			added += other.added;
			updated += other.updated;
			markedUnavailable += other.markedUnavailable;
		}

	}

}
