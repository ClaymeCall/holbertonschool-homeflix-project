package com.homeflix.library.downloads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.homeflix.library.indexing.MediaFileClassifier;
import com.homeflix.library.indexing.MediaFileKind;
import com.homeflix.library.roots.Root;
import com.homeflix.library.roots.RootService;
import com.homeflix.library.web.RejectedRequestException;

/**
 * What the acquisition service needs from the library: show a title as downloading, then link its file when it
 * is complete (docs/architecture/consistency-rules.md, "Acquisition handoff"; requirements CAT-01, ACQ-07, LIB-05).
 *
 * <p>Every call is repeatable, because acquisition repeats a call until it works. The library never reads a
 * download folder here: it checks the one file it is told about, with the same path rules as a stream (LIB-08).
 */
@Service
public class DownloadService {

	private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

	private static final int MAX_TITLE_LENGTH = 500;

	private final DownloadRepository downloads;
	private final RootService roots;
	private final Path downloadsFolder;
	private final TransactionTemplate transaction;

	DownloadService(DownloadRepository downloads, RootService roots,
			@Value("${homeflix.library.downloads-path:/media/downloads}") String downloadsFolder,
			PlatformTransactionManager transactionManager) {
		this.downloads = downloads;
		this.roots = roots;
		this.downloadsFolder = Path.of(downloadsFolder);
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * A download of this entry starts. Creates the entry as {@code DOWNLOADING}, or makes an entry that failed
	 * before {@code DOWNLOADING} again and keeps what an administrator edited.
	 *
	 * @throws RejectedRequestException 409 {@code already_in_library} when the title already has a file
	 */
	public void starting(UUID entryId, String title) {
		String cleanTitle = title.strip();
		if (cleanTitle.isEmpty() || cleanTitle.length() > MAX_TITLE_LENGTH) {
			throw new RejectedRequestException(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.");
		}
		transaction.executeWithoutResult(status -> {
			downloads.insertDownloading(entryId, cleanTitle);
			DownloadRepository.Item item = downloads.lockItem(entryId).orElseThrow();
			if (item.fileId() != null) {
				// Available, or unavailable because its file is gone: the title is in the library either way.
				throw alreadyInLibrary();
			}
			if (!"DOWNLOADING".equals(item.availability())) {
				downloads.setAvailability(item.id(), "DOWNLOADING");
			}
		});
	}

	/**
	 * The file of this download is complete and verified. Links it to the entry and makes the entry available,
	 * in one transaction. The file is checked here too, so a path that leads outside the downloads folder is
	 * refused whatever the caller says (R-01).
	 *
	 * @return the id of the catalogue entry
	 * @throws RejectedRequestException 400 {@code invalid_path}, 404 {@code not_found} (no such download),
	 *         409 {@code downloads_folder_not_authorized}, {@code file_not_available}, {@code not_downloading}
	 *         or {@code already_in_library}
	 */
	public UUID completed(UUID entryId, String relativePath) {
		Path relative = relativePathOf(relativePath);
		Root root = downloadsRoot();
		Path realRoot = realOf(Path.of(root.path()));
		Found found = check(realRoot, relative);

		return transaction.execute(status -> {
			DownloadRepository.Item item = downloads.lockItem(entryId).orElseThrow(
					com.homeflix.library.web.NotFoundException::new);

			if ("AVAILABLE".equals(item.availability())) {
				// Repeated call: the same file answers 200, another one is refused.
				boolean same = item.fileId() != null && downloads.findFile(item.fileId())
						.filter(f -> f.rootId().equals(root.id()) && f.relativePath().equals(found.relativePath())).isPresent();
				if (!same) {
					throw alreadyInLibrary();
				}
				return item.id();
			}
			if (!"DOWNLOADING".equals(item.availability())) {
				// A failure was already notified: a late completion must not bring a cancelled download back.
				log.warn("Completed download refused, entry {} is {}", entryId, item.availability());
				throw new RejectedRequestException(HttpStatus.CONFLICT, "not_downloading",
						"This title is not being downloaded.");
			}

			OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
			Optional<UUID> known = downloads.findFileId(root.id(), found.relativePath());
			UUID fileId;
			if (known.isPresent()) {
				// A scan found the file first: the download entry wins, the scan's entry goes.
				fileId = known.get();
				downloads.deleteScanEntryOf(fileId);
				downloads.markSeen(fileId, found.sizeBytes(), now);
			}
			else {
				fileId = downloads.insertFile(root.id(), found.relativePath(), found.sizeBytes(), now);
			}
			downloads.linkFile(item.id(), fileId);
			log.info("Download of entry {} is now in the library", entryId);
			return item.id();
		});
	}

	/**
	 * The download failed or was cancelled. A downloading entry becomes {@code UNAVAILABLE}, keeping its title.
	 * Anything else is left alone, including an entry that is already available, and an unknown entry: the call
	 * is repeated until it works, so it must be harmless to repeat.
	 */
	public void failed(UUID entryId) {
		transaction.executeWithoutResult(status -> downloads.lockItem(entryId).ifPresent(item -> {
			if ("DOWNLOADING".equals(item.availability())) {
				downloads.setAvailability(item.id(), "UNAVAILABLE");
			}
		}));
	}

	/** The relative path, refused unless it is a plain path inside a folder: no {@code ..}, no root, no odd character. */
	private static Path relativePathOf(String relativePath) {
		if (relativePath == null || relativePath.isBlank() || relativePath.length() > 4096
				|| relativePath.startsWith("/") || relativePath.indexOf('\\') >= 0 || relativePath.indexOf('\0') >= 0) {
			throw invalidPath();
		}
		for (String part : relativePath.split("/", -1)) {
			if (part.isEmpty() || part.equals(".") || part.equals("..")) {
				throw invalidPath();
			}
		}
		try {
			return Path.of(relativePath);
		}
		catch (InvalidPathException e) {
			throw invalidPath();
		}
	}

	/** The downloads folder must be an authorized root (consistency rules, "Completing", step 2). */
	private Root downloadsRoot() {
		Path wanted = realOf(downloadsFolder);
		return roots.list().stream().filter(root -> realOf(Path.of(root.path())).equals(wanted)).findFirst()
				.orElseThrow(() -> {
					log.warn("Completed download refused: {} is not an authorized folder. Add it in the administration area.",
							downloadsFolder);
					return new RejectedRequestException(HttpStatus.CONFLICT, "downloads_folder_not_authorized",
							"The downloads folder is not authorized.");
				});
	}

	/**
	 * The file must exist, be a readable regular file inside the downloads folder once links are followed, and
	 * be a supported video. Its stored path is the real one, so a link inside the folder cannot hide a file.
	 */
	private static Found check(Path realRoot, Path relative) {
		Path real;
		try {
			real = realRoot.resolve(relative).normalize().toRealPath();
		}
		catch (IOException e) {
			log.warn("Completed download refused, the file cannot be resolved: {} / {}", realRoot, relative, e);
			throw notAvailable();
		}
		if (!real.startsWith(realRoot)) {
			log.warn("Completed download refused, it leads outside the downloads folder: {} -> {}", relative, real);
			throw invalidPath();
		}
		try {
			if (!Files.isRegularFile(real) || !Files.isReadable(real)
					|| MediaFileClassifier.classify(real.getFileName().toString()) != MediaFileKind.SUPPORTED_VIDEO) {
				log.warn("Completed download refused, not a readable supported video: {}", real);
				throw notAvailable();
			}
			String stored = realRoot.relativize(real).toString();
			return new Found(stored, Files.size(real));
		}
		catch (IOException e) {
			log.warn("Completed download refused, the file cannot be read: {}", real, e);
			throw notAvailable();
		}
	}

	private static Path realOf(Path path) {
		try {
			return path.toRealPath();
		}
		catch (IOException e) {
			return path.toAbsolutePath().normalize();
		}
	}

	private record Found(String relativePath, long sizeBytes) {
	}

	private static RejectedRequestException invalidPath() {
		return new RejectedRequestException(HttpStatus.BAD_REQUEST, "invalid_path", "This file path is not valid.");
	}

	private static RejectedRequestException notAvailable() {
		return new RejectedRequestException(HttpStatus.CONFLICT, "file_not_available",
				"The downloaded file cannot be used.");
	}

	private static RejectedRequestException alreadyInLibrary() {
		return new RejectedRequestException(HttpStatus.CONFLICT, "already_in_library",
				"This title is already in the library.");
	}

}
