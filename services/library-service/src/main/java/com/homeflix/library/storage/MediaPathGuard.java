package com.homeflix.library.storage;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.homeflix.library.storage.MediaAccessDeniedException.Reason;

/**
 * Last line of defence before a media file is opened (LIB-08, PLY-04, risks R-01 and R-04).
 *
 * <p>Clients only ever send media IDs, never paths. The path the service reads comes from
 * its own database, and is still checked here: the <em>real</em> path is resolved first,
 * following symbolic links and removing {@code ..}, and only then compared with the real
 * path of each authorized root. Comparison is by path component, so
 * {@code /media/library-evil} is not inside {@code /media/library}.
 */
public final class MediaPathGuard {

	private static final Logger log = LoggerFactory.getLogger(MediaPathGuard.class);

	private final List<Path> roots;

	/** @param authorizedRoots the configured roots; each is resolved to its real path, so a symlinked root works */
	public MediaPathGuard(List<Path> authorizedRoots) {
		this.roots = authorizedRoots.stream().map(MediaPathGuard::realPathOrNull)
				.filter(Objects::nonNull).toList();
	}

	/**
	 * Returns the real path of {@code candidate} if it exists and lies inside an authorized root.
	 *
	 * @throws MediaAccessDeniedException {@code NOT_FOUND} if it cannot be resolved,
	 *         {@code OUTSIDE_ROOTS} if its real path is outside every root
	 */
	public Path resolve(Path candidate) {
		Path real;
		try {
			real = candidate.toRealPath();
		}
		catch (NoSuchFileException e) {
			log.warn("Media file not found: {}", candidate);
			throw new MediaAccessDeniedException(Reason.NOT_FOUND, e);
		}
		catch (IOException e) {
			log.warn("Media file could not be resolved: {}", candidate, e);
			throw new MediaAccessDeniedException(Reason.NOT_FOUND, e);
		}
		for (Path root : roots) {
			if (real.startsWith(root)) {
				return real;
			}
		}
		log.warn("Refused access outside the authorized roots: requested={} real={}", candidate, real);
		throw new MediaAccessDeniedException(Reason.OUTSIDE_ROOTS, null);
	}

	private static Path realPathOrNull(Path root) {
		try {
			return root.toRealPath();
		}
		catch (IOException e) {
			log.warn("Authorized root is not accessible and is ignored: {}", root, e);
			return null;
		}
	}

}
