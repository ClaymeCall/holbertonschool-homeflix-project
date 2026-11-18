package com.homeflix.library.playback;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.homeflix.library.security.AccessGuard;
import com.homeflix.library.storage.MediaAccessDeniedException;
import com.homeflix.library.storage.MediaAccessDeniedException.Reason;
import com.homeflix.library.storage.MediaPathGuard;
import com.homeflix.library.web.NotFoundException;
import com.homeflix.library.web.RejectedRequestException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Streams the file of a title with HTTP Range (docs/api/library.yaml, {@code streamMedia}; PLY-03, PLY-04).
 *
 * <p>The session cookie is checked on <em>every</em> request, since the browser's {@code <video>} element
 * sends a request for each part it needs. The path comes from the database and is checked by
 * {@link MediaPathGuard} against the title's own folder after following links (R-01, R-04). Nothing
 * about the file system is ever in an error body (SEC-04).
 */
@RestController
class StreamController {

	private static final Logger log = LoggerFactory.getLogger(StreamController.class);

	private static final String VIDEO_TYPE = "video/mp4";
	private static final int BUFFER_SIZE = 64 * 1024;

	private final StreamRepository targets;
	private final AccessGuard access;

	StreamController(StreamRepository targets, AccessGuard access) {
		this.targets = targets;
		this.access = access;
	}

	@GetMapping("/media/{mediaId}/stream")
	void stream(HttpServletRequest request, HttpServletResponse response, @PathVariable UUID mediaId)
			throws IOException {
		access.requireAccount(request);
		Path file = locate(mediaId);
		long size = Files.size(file);

		RangeRequest range = RangeRequest.parse(request.getHeader(HttpHeaders.RANGE), size);
		response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
		response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");

		switch (range) {
			case RangeRequest.Unsatisfiable u -> {
				response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
				throw new RejectedRequestException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "range_not_satisfiable",
						"This part of the video is not available.");
			}
			case RangeRequest.Whole w -> {
				response.setStatus(HttpStatus.OK.value());
				response.setContentType(VIDEO_TYPE);
				response.setContentLengthLong(size);
				send(file, 0, size, response);
			}
			case RangeRequest.Partial p -> {
				response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
				response.setContentType(VIDEO_TYPE);
				response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + p.start() + "-" + p.end() + "/" + size);
				response.setContentLengthLong(p.length());
				send(file, p.start(), p.length(), response);
			}
		}
	}

	/**
	 * The real path of the title's file, once it is known to exist and to lie inside the title's folder.
	 * A title with no file yet is still downloading (409) or has lost its file (404).
	 */
	private Path locate(UUID mediaId) {
		StreamTarget target = targets.find(mediaId).orElseThrow(NotFoundException::new);
		if (target.rootPath() == null) {
			if ("DOWNLOADING".equals(target.availability())) {
				throw new RejectedRequestException(HttpStatus.CONFLICT, "media_not_available",
						"This title is still downloading and cannot be played yet.");
			}
			log.warn("Title {} has no file", mediaId);
			throw new MediaAccessDeniedException(Reason.NOT_FOUND, null);
		}
		Path candidate = Path.of(target.rootPath()).resolve(target.relativePath());
		Path real = new MediaPathGuard(List.of(Path.of(target.rootPath()))).resolve(candidate);
		if (!Files.isRegularFile(real)) {
			log.warn("Title {} points to something that is not a file: {}", mediaId, real);
			throw new MediaAccessDeniedException(Reason.NOT_FOUND, new NoSuchFileException(real.toString()));
		}
		return real;
	}

	/** Copies {@code length} bytes from {@code start}. A viewer who closes the page mid-way is not an error. */
	private static void send(Path file, long start, long length, HttpServletResponse response) throws IOException {
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
			channel.position(start);
			OutputStream out = response.getOutputStream();
			long remaining = length;
			var in = Channels.newInputStream(channel);
			byte[] chunk = new byte[(int) Math.min(BUFFER_SIZE, Math.max(length, 1))];
			while (remaining > 0) {
				int read = in.read(chunk, 0, (int) Math.min(chunk.length, remaining));
				if (read < 0) {
					break;
				}
				out.write(chunk, 0, read);
				remaining -= read;
			}
			out.flush();
		}
		catch (IOException e) {
			if (isClientGone(e)) {
				log.debug("The viewer stopped the stream: {}", e.toString());
				return;
			}
			throw e;
		}
	}

	private static boolean isClientGone(IOException e) {
		String name = e.getClass().getSimpleName();
		String message = String.valueOf(e.getMessage());
		return name.contains("ClientAbort") || message.contains("Broken pipe") || message.contains("Connection reset");
	}

}
