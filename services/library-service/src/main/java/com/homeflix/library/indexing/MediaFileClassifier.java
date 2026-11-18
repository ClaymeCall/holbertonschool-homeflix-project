package com.homeflix.library.indexing;

import java.util.Locale;
import java.util.Set;

/**
 * Decides, from the file name alone, whether a file is a supported video, an
 * unsupported video, or something to ignore (ADR-0015, requirements LIB-02 and PLY-01).
 *
 * <p>The rules look only at the name: no file is opened or read. Content checks
 * (codecs, faststart) are out of scope for the MVP.
 */
public final class MediaFileClassifier {

	/** The only supported container: MP4 with H.264 video and AAC audio (ADR-0015). */
	static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4");

	/** Video containers that are recognised but not supported: reported as "format not supported". */
	static final Set<String> UNSUPPORTED_VIDEO_EXTENSIONS = Set.of(
			"mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg",
			"ts", "m2ts", "3gp", "ogv", "hevc", "h265");

	private MediaFileClassifier() {
	}

	public static MediaFileKind classify(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return MediaFileKind.IGNORED;
		}
		// Hidden files, including macOS "._name.mp4" companion files, are never media.
		if (fileName.startsWith(".")) {
			return MediaFileKind.IGNORED;
		}
		String extension = extensionOf(fileName);
		if (SUPPORTED_EXTENSIONS.contains(extension)) {
			return MediaFileKind.SUPPORTED_VIDEO;
		}
		if (UNSUPPORTED_VIDEO_EXTENSIONS.contains(extension)) {
			return MediaFileKind.UNSUPPORTED_VIDEO;
		}
		// Everything else, including partial downloads (".part", ".!qB") and sidecar files.
		return MediaFileKind.IGNORED;
	}

	private static String extensionOf(String fileName) {
		int dot = fileName.lastIndexOf('.');
		if (dot < 0 || dot == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

}
