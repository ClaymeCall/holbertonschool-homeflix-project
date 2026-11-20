package com.homeflix.acquisition.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
 * folder (a torrent of several files is refused).</li>
 * <li>Its name ends with {@code .mp4}, the only format the library indexes (ADR-0015).</li>
 * <li>Its size is exactly the catalogue entry's expected size.</li>
 * <li>Its structure is an MP4 with H.264 video and AAC sound ({@link Mp4Inspector}).</li>
 * </ol>
 */
@Component
public class DownloadedFileVerifier {

	/** The outcome: the path relative to the downloads folder, or why the file is refused (for the log). */
	public sealed interface Result permits Accepted, Refused {
	}

	public record Accepted(String relativePath) implements Result {
	}

	public record Refused(String reason) implements Result {
	}

	private final Path folder;

	public DownloadedFileVerifier(DownloadsProperties settings) {
		this.folder = Path.of(settings.folder()).toAbsolutePath().normalize();
	}

	/**
	 * @param savePath the torrent's save path, as the torrent client sees it ({@code /downloads/})
	 * @param contentPath the torrent's content path, as the torrent client sees it ({@code /downloads/x.mp4})
	 * @param expectedSize the catalogue entry's expected size in bytes
	 */
	public Result verify(String savePath, String contentPath, long expectedSize) {
		Optional<String> relative = relativePath(savePath, contentPath);
		if (relative.isEmpty()) {
			return new Refused("content path " + contentPath + " is not a file inside the save path " + savePath);
		}
		Path file = folder.resolve(relative.get()).normalize();
		try {
			if (!file.startsWith(folder) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
					|| !file.toRealPath().startsWith(folder.toRealPath())) {
				return new Refused(relative.get() + " is not a regular file inside the downloads folder");
			}
			if (!relative.get().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
				return new Refused(relative.get() + " is not an .mp4 file");
			}
			long size = Files.size(file);
			if (size != expectedSize) {
				return new Refused(relative.get() + " is " + size + " bytes, " + expectedSize + " expected");
			}
			Optional<String> problem = Mp4Inspector.problem(file);
			if (problem.isPresent()) {
				return new Refused(relative.get() + " is not a supported MP4: " + problem.get());
			}
		}
		catch (IOException ex) {
			return new Refused(relative.get() + " cannot be read: " + ex.getMessage());
		}
		return new Accepted(relative.get());
	}

	/** The content path relative to the save path, with no {@code ..} and no absolute part. */
	static Optional<String> relativePath(String savePath, String contentPath) {
		if (savePath == null || contentPath == null || savePath.isBlank()) {
			return Optional.empty();
		}
		String prefix = savePath.endsWith("/") ? savePath : savePath + "/";
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
