package com.homeflix.acquisition.verification;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks that a file is an MP4 with H.264 video and, if it has sound, AAC audio: the only format HomeFlix plays
 * (ADR-0015). It reads the structure of the file (its boxes) and never decodes or runs anything (R-19).
 *
 * <ul>
 * <li>The top-level boxes cover the whole file exactly, the first one is {@code ftyp} with an MP4 brand, and
 * there is one {@code moov} and at least one {@code mdat}.</li>
 * <li>At least one video track, and every video track is H.264 ({@code avc1} or {@code avc3}).</li>
 * <li>Every sound track is AAC ({@code mp4a}). Other tracks (subtitles, chapters) are allowed.</li>
 * </ul>
 */
public final class Mp4Inspector {

	static final Set<String> BRANDS = Set.of("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1",
			"M4V ");
	static final Set<String> VIDEO = Set.of("avc1", "avc3");
	static final Set<String> SOUND = Set.of("mp4a");

	/** The metadata box is read into memory: above this size the file is refused. */
	static final long MAX_MOOV_BYTES = 64L * 1024 * 1024;
	static final int MAX_BOXES = 10_000;

	private Mp4Inspector() {
	}

	/** Why the file is not a supported MP4, for the log only, or empty when it is one. */
	public static Optional<String> problem(Path file) throws IOException {
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
			return problem(channel);
		}
		catch (Malformed ex) {
			return Optional.of(ex.getMessage());
		}
	}

	private static Optional<String> problem(FileChannel channel) throws IOException {
		long size = channel.size();
		long offset = 0;
		int count = 0;
		boolean mediaData = false;
		ByteBuffer movie = null;
		while (offset < size) {
			if (++count > MAX_BOXES) {
				return Optional.of("more than " + MAX_BOXES + " top-level boxes");
			}
			if (size - offset < 8) {
				return Optional.of("truncated box header at " + offset);
			}
			ByteBuffer header = read(channel, offset, (int) Math.min(16, size - offset));
			long boxSize = Integer.toUnsignedLong(header.getInt(0));
			String type = type(header, 4);
			int headerSize = 8;
			if (boxSize == 1) {
				if (header.limit() < 16) {
					return Optional.of("truncated box header at " + offset);
				}
				boxSize = header.getLong(8);
				headerSize = 16;
			}
			else if (boxSize == 0) {
				boxSize = size - offset;
			}
			if (boxSize < headerSize || boxSize > size - offset) {
				return Optional.of("box " + type + " at " + offset + " does not fit in the file");
			}
			if (count == 1 && !type.equals("ftyp")) {
				return Optional.of("the first box is " + type + ", not ftyp");
			}
			switch (type) {
				case "ftyp" -> {
					if (count != 1) {
						return Optional.of("a second ftyp box");
					}
					ByteBuffer brands = read(channel, offset + headerSize, (int) Math.min(boxSize - headerSize, 1024));
					if (!hasMp4Brand(brands)) {
						return Optional.of("no MP4 brand in ftyp");
					}
				}
				case "moov" -> {
					if (movie != null) {
						return Optional.of("a second moov box");
					}
					if (boxSize - headerSize > MAX_MOOV_BYTES) {
						return Optional.of("moov box larger than " + MAX_MOOV_BYTES + " bytes");
					}
					movie = read(channel, offset + headerSize, (int) (boxSize - headerSize));
				}
				case "mdat" -> mediaData = true;
				default -> {
					// free, udta, uuid... are allowed and skipped.
				}
			}
			offset += boxSize;
		}
		if (movie == null) {
			return Optional.of("no moov box");
		}
		if (!mediaData) {
			return Optional.of("no mdat box");
		}
		return tracks(movie);
	}

	private static boolean hasMp4Brand(ByteBuffer ftyp) {
		if (ftyp.limit() < 8) {
			return false;
		}
		if (BRANDS.contains(type(ftyp, 0))) {
			return true;
		}
		for (int at = 8; at + 4 <= ftyp.limit(); at += 4) {
			if (BRANDS.contains(type(ftyp, at))) {
				return true;
			}
		}
		return false;
	}

	private static Optional<String> tracks(ByteBuffer movie) {
		int videoTracks = 0;
		for (ByteBuffer track : children(movie, "trak")) {
			ByteBuffer media = only(track, "mdia");
			ByteBuffer handler = only(media, "hdlr");
			if (handler.limit() < 12) {
				throw new Malformed("truncated hdlr box");
			}
			String kind = type(handler, 8);
			List<String> codecs = sampleEntries(only(only(only(media, "minf"), "stbl"), "stsd"));
			if (kind.equals("vide")) {
				videoTracks++;
				if (codecs.isEmpty() || !VIDEO.containsAll(codecs)) {
					return Optional.of("video track that is not H.264: " + codecs);
				}
			}
			else if (kind.equals("soun") && (codecs.isEmpty() || !SOUND.containsAll(codecs))) {
				return Optional.of("sound track that is not AAC: " + codecs);
			}
		}
		return videoTracks == 0 ? Optional.of("no video track") : Optional.empty();
	}

	/** The type of each sample entry of an {@code stsd} box (the codec of the track). */
	private static List<String> sampleEntries(ByteBuffer stsd) {
		if (stsd.limit() < 8) {
			throw new Malformed("truncated stsd box");
		}
		long entries = Integer.toUnsignedLong(stsd.getInt(4));
		List<String> types = new ArrayList<>();
		int at = 8;
		for (long i = 0; i < entries; i++) {
			if (at + 8 > stsd.limit()) {
				throw new Malformed("truncated sample entry");
			}
			long entrySize = Integer.toUnsignedLong(stsd.getInt(at));
			if (entrySize < 8 || at + entrySize > stsd.limit()) {
				throw new Malformed("sample entry does not fit in stsd");
			}
			types.add(type(stsd, at + 4));
			at += (int) entrySize;
		}
		return types;
	}

	private static ByteBuffer only(ByteBuffer parent, String type) {
		List<ByteBuffer> found = children(parent, type);
		if (found.size() != 1) {
			throw new Malformed(found.size() + " " + type + " boxes where one is expected");
		}
		return found.get(0);
	}

	/** The contents of the child boxes of this type, in order. */
	private static List<ByteBuffer> children(ByteBuffer parent, String type) {
		List<ByteBuffer> found = new ArrayList<>();
		int at = 0;
		while (at < parent.limit()) {
			if (parent.limit() - at < 8) {
				throw new Malformed("truncated box header inside moov");
			}
			long size = Integer.toUnsignedLong(parent.getInt(at));
			int headerSize = 8;
			if (size == 1) {
				if (parent.limit() - at < 16) {
					throw new Malformed("truncated box header inside moov");
				}
				size = parent.getLong(at + 8);
				headerSize = 16;
			}
			else if (size == 0) {
				size = parent.limit() - at;
			}
			if (size < headerSize || size > parent.limit() - at) {
				throw new Malformed("box inside moov does not fit in its parent");
			}
			if (type(parent, at + 4).equals(type)) {
				found.add(parent.slice(at + headerSize, (int) size - headerSize));
			}
			at += (int) size;
		}
		return found;
	}

	private static ByteBuffer read(FileChannel channel, long position, int length) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(length);
		while (buffer.hasRemaining()) {
			if (channel.read(buffer, position + buffer.position()) < 0) {
				throw new Malformed("unexpected end of file");
			}
		}
		return buffer.flip();
	}

	private static String type(ByteBuffer buffer, int at) {
		byte[] bytes = new byte[4];
		buffer.get(at, bytes);
		return new String(bytes, StandardCharsets.ISO_8859_1);
	}

	/** A structure that does not follow the MP4 format. */
	private static final class Malformed extends RuntimeException {

		Malformed(String message) {
			super(message, null, false, false);
		}

	}

}
