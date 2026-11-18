package com.homeflix.library.roots;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.homeflix.library.web.RejectedRequestException;

/**
 * Decides whether a folder may become an authorized root (LIB-01, LIB-03, risk R-02).
 *
 * <p>The folder must lie inside one of the paths Docker mounts into the container, exist, be a
 * directory and be readable. Each failure has its own code so the interface can explain it. The
 * message never contains a path or an exception (SEC-04): the details are logged.
 *
 * <p>The "inside the mounted paths" check comes first and is done on the text of the path, so the
 * answer for a path outside them does not say whether it exists. It is repeated on the real path,
 * so a symbolic link cannot lead out of the mounted paths (R-04).
 */
@Component
public class RootValidator {

	private static final Logger log = LoggerFactory.getLogger(RootValidator.class);

	/** A folder that passed every check: the path to store and its real path. */
	public record Accepted(String path, Path realPath) {
	}

	private final List<Path> mountedPaths;

	public RootValidator(
			@Value("${homeflix.library.mounted-paths:/media/library,/media/downloads}") List<String> mountedPaths) {
		this.mountedPaths = mountedPaths.stream().map(Path::of).map(Path::normalize).toList();
	}

	public Accepted validate(String requested) {
		Path path = parse(requested);

		if (!isInsideMountedPaths(path)) {
			log.warn("Folder refused, outside the mounted paths: {}", path);
			throw refused("directory_outside_library", "This folder is not inside the folders shared with HomeFlix.");
		}
		if (!Files.exists(path)) {
			log.warn("Folder refused, it does not exist: {}", path);
			throw refused("directory_not_found", "This folder does not exist.");
		}
		Path real;
		try {
			real = path.toRealPath();
		}
		catch (IOException e) {
			log.warn("Folder refused, its real path cannot be resolved: {}", path, e);
			throw refused("directory_not_readable", "HomeFlix cannot read this folder.");
		}
		if (!isInsideMountedPaths(real)) {
			log.warn("Folder refused, a link leads outside the mounted paths: {} -> {}", path, real);
			throw refused("directory_outside_library", "This folder is not inside the folders shared with HomeFlix.");
		}
		if (!Files.isDirectory(real)) {
			log.warn("Folder refused, it is not a directory: {}", real);
			throw refused("not_a_directory", "This path is a file, not a folder.");
		}
		if (!canList(real)) {
			throw refused("directory_not_readable", "HomeFlix cannot read this folder.");
		}
		return new Accepted(path.toString(), real);
	}

	/** An absolute path with no {@code ..} and no trailing slash, or a validation error on the field. */
	private static Path parse(String requested) {
		Path path;
		try {
			path = Path.of(requested.trim());
		}
		catch (InvalidPathException e) {
			throw invalidPath();
		}
		if (!path.isAbsolute()) {
			throw invalidPath();
		}
		return path.normalize();
	}

	private static RejectedRequestException invalidPath() {
		return new RejectedRequestException(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.",
				Map.of("path", "Enter the full path of a folder, starting with /."));
	}

	private boolean isInsideMountedPaths(Path candidate) {
		for (Path mounted : mountedPaths) {
			if (candidate.startsWith(mounted) || candidate.startsWith(realOrSelf(mounted))) {
				return true;
			}
		}
		return false;
	}

	private static Path realOrSelf(Path path) {
		try {
			return path.toRealPath();
		}
		catch (IOException e) {
			return path;
		}
	}

	private static boolean canList(Path directory) {
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
			entries.iterator().hasNext();
			return true;
		}
		catch (IOException | RuntimeException e) {
			log.warn("Folder refused, it cannot be read: {}", directory, e);
			return false;
		}
	}

	private static RejectedRequestException refused(String code, String message) {
		return new RejectedRequestException(HttpStatus.BAD_REQUEST, code, message);
	}

}
