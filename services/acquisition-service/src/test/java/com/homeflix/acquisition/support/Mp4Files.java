package com.homeflix.acquisition.support;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Builds small MP4 files box by box, so the tests need no video file in the repository (no media is
 * committed, CONTRIBUTING). Only the boxes the checks read are filled: the media data is zeros.
 */
public final class Mp4Files {

	private Mp4Files() {
	}

	/** An MP4 with an H.264 video track and an AAC sound track, padded with media data to exactly {@code size}. */
	public static byte[] valid(int size) {
		return padded(size, ftyp("isom", "isom", "iso2", "avc1", "mp41"), movie("avc1", "mp4a"), box("free"));
	}

	/** The given boxes, then an {@code mdat} that brings the file to exactly {@code size} bytes. */
	public static byte[] padded(int size, byte[]... boxes) {
		byte[] head = concat(boxes);
		if (size < head.length + 8) {
			throw new IllegalArgumentException("size too small for the boxes");
		}
		return concat(head, box("mdat", new byte[size - head.length - 8]));
	}

	public static byte[] ftyp(String majorBrand, String... compatibleBrands) {
		ByteArrayOutputStream payload = new ByteArrayOutputStream();
		payload.writeBytes(ascii(majorBrand));
		payload.writeBytes(new byte[] { 0, 0, 2, 0 });
		for (String brand : compatibleBrands) {
			payload.writeBytes(ascii(brand));
		}
		return box("ftyp", payload.toByteArray());
	}

	/** A {@code moov} with one video track of this codec and, unless null, one sound track of that codec. */
	public static byte[] movie(String videoCodec, String soundCodec) {
		return soundCodec == null ? box("moov", track("vide", videoCodec))
				: box("moov", track("vide", videoCodec), track("soun", soundCodec));
	}

	/** A track: {@code trak/mdia/hdlr} with its handler, and {@code minf/stbl/stsd} with one sample entry. */
	public static byte[] track(String handler, String codec) {
		byte[] hdlr = box("hdlr", concat(new byte[8], ascii(handler), new byte[12]));
		byte[] stsd = box("stsd", concat(new byte[] { 0, 0, 0, 0, 0, 0, 0, 1 }, box(codec, new byte[16])));
		return box("trak", box("mdia", hdlr, box("minf", box("stbl", stsd))));
	}

	public static byte[] box(String type, byte[]... payload) {
		byte[] content = concat(payload);
		return concat(ByteBuffer.allocate(4).putInt(8 + content.length).array(), ascii(type), content);
	}

	public static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] part : parts) {
			out.writeBytes(part);
		}
		return out.toByteArray();
	}

	private static byte[] ascii(String fourCharacters) {
		byte[] bytes = fourCharacters.getBytes(StandardCharsets.ISO_8859_1);
		if (bytes.length != 4) {
			throw new IllegalArgumentException("a box type or brand has four characters: " + fourCharacters);
		}
		return bytes;
	}

}
