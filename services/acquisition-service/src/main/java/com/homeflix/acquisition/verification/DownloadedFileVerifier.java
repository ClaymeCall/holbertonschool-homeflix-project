package com.homeflix.acquisition.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.homeflix.acquisition.config.DownloadsProperties;

/**
 * Checks a completed download before it is handed to the library (J7; ACQ-06, R-19,
 * docs/architecture/consistency-rules.md, "Completing"). The downloads folder is mounted read-only in this
 * service: the file is only read, never changed or run.
 *
 * <ol>
 * <li>The torrent's content is one file inside the downloads folder: no {@code ..}, no symbolic link, not a
 * folder (a torrent of several files is refused). Its path is taken from the downloads folder, whatever
 * subfolder the client saved it in.</li>
 * <li>Its name ends with {@code .mp4}, the only format the library indexes (ADR-0015).</li>
 * <li>Its size is exactly the catalogue entry's expected size.</li>
 * <li>Its structure is an MP4 with H.264 video and AAC sound ({@link Mp4Inspector}).</li>
 * </ol>
 *
 * A file that cannot be read is not refused: a permission or I/O problem says nothing about the file, and a
 * refused file is deleted.
 */
@Component
public class DownloadedFileVerifier {

	/**
	 * The outcome: the path relative to the downloads folder, why the file is refused, or why it cannot be
	 * read yet (for the log).
	 */
	public sealed interface Result permits Accepted, Refused, Unreadable {
	}

	public record Accepted(String relativePath) implements Result {
	}

	/** The file is not the expected video: it must not be kept. */
	public record Refused(String reason) implements Result {
	}

	/**
	 * The file cannot be read (a permission or an I/O problem), so nothing can be said about it: it must not
	 * be deleted, and is checked again later.
	 */
	public record Unreadable(String reason) implements Result {
	}

	private final Path folder;

	public DownloadedFileVerifier(DownloadsProperties settings) {
		this.folder = Path.of(settings.folder()).toAbsolutePath().normalize();
	}

	/**
	 * @param contentPath the torrent's content path, as the torrent client sees it: the client saves to the
	 *        downloads folder, mounted here at the same path ({@code /downloads/Sintel/x.mp4})
	 * @param expectedSize the catalogue entry's expected size in bytes
	 */
	public Result verify(String contentPath, long expectedSize) {
		Optional<String> relative = relativePath(folder, contentPath);
		if (relative.isEmpty()) {
			return new Refused("content path " + contentPath + " is not a file inside the downloads folder " + folder);
		}
		Path file = folder.resolve(relative.get()).normalize();
		try {
			BasicFileAttributes attributes;
			try {
				attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			}
			catch (NoSuchFileException ex) {
				return new Refused(relative.get() + " does not exist in the downloads folder");
			}
			if (!file.startsWith(folder) || !attributes.isRegularFile()
					|| !file.toRealPath().startsWith(folder.toRealPath())) {
				return new Refused(relative.get() + " is not a regular file inside the downloads folder");
			}
			if (!relative.get().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
				return new Refused(relative.get() + " is not an .mp4 file");
			}
			if (attributes.size() != expectedSize) {
				return new Refused(relative.get() + " is " + attributes.size() + " bytes, " + expectedSize + " expected");
			}
			Optional<String> problem = Mp4Inspector.problem(file);
			if (problem.isPresent()) {
				return new Refused(relative.get() + " is not a supported MP4: " + problem.get());
			}
		}
		catch (IOException ex) {
			return new Unreadable(relative.get() + " cannot be read: " + ex);
		}
		return new Accepted(relative.get());
	}

	/** The content path relative to the downloads folder, with no {@code ..} and no absolute part. */
	static Optional<String> relativePath(Path folder, String contentPath) {
		if (contentPath == null) {
			return Optional.empty();
		}
		String prefix = folder.toString().endsWith("/") ? folder.toString() : folder + "/";
		if (!contentPath.startsWith(prefix)) {
			return Optional.empty();
		}
		String relative = contentPath.substring(prefix.length());
		if (relative.isEmpty() || relative.startsWith("/") || relative.contains("\\") || relative.contains("\0")) {
			return Optional.empty();
		}
		for (String part : relative.split("/", -1)) {
			if (part.isEmpty() || part.equals(".") || part.equals("..")) {
				return Optional.empty();
			}
		}
		return Optional.of(relative);
	}

}
