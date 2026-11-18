package com.homeflix.library.indexing;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a walk of one root found. Only regular files are looked at: a symbolic link, to a file or to a
 * folder, is never followed, so a link cannot make the scan leave the root (LIB-08, R-04) or find the
 * same file twice (LIB-05).
 */
final class FoundFiles {

	private static final Logger log = LoggerFactory.getLogger(FoundFiles.class);

	/** Longest relative path the database accepts. */
	private static final int MAX_PATH_LENGTH = 4096;

	record Video(String relativePath, long sizeBytes) {
	}

	private final List<Video> supported = new ArrayList<>();
	private final List<String> unsupported = new ArrayList<>();
	private boolean complete = true;

	List<Video> supported() {
		return supported;
	}

	List<String> unsupported() {
		return unsupported;
	}

	/**
	 * False when part of the folder could not be read. The scan then cannot tell a file that is gone from
	 * a file it could not see, so it must not mark anything unavailable for this root.
	 */
	boolean complete() {
		return complete;
	}

	static FoundFiles walk(Path realRoot) throws IOException {
		FoundFiles found = new FoundFiles();
		Files.walkFileTree(realRoot, new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (!attrs.isRegularFile()) {
					return FileVisitResult.CONTINUE;
				}
				MediaFileKind kind = MediaFileClassifier.classify(file.getFileName().toString());
				if (kind == MediaFileKind.IGNORED) {
					return FileVisitResult.CONTINUE;
				}
				String relative = relativePath(realRoot, file);
				if (relative.length() > MAX_PATH_LENGTH) {
					log.warn("File skipped, its path is too long: {} characters", relative.length());
					return FileVisitResult.CONTINUE;
				}
				if (kind == MediaFileKind.SUPPORTED_VIDEO) {
					found.supported.add(new Video(relative, attrs.size()));
				}
				else {
					found.unsupported.add(relative);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException e) {
				log.warn("A file or folder could not be read during the scan: {}", file, e);
				found.complete = false;
				return FileVisitResult.CONTINUE;
			}

		});
		return found;
	}

	private static String relativePath(Path root, Path file) {
		return root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
	}

}
