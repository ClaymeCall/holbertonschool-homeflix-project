package com.homeflix.acquisition.verification;

import static com.homeflix.acquisition.support.Mp4Files.box;
import static com.homeflix.acquisition.support.Mp4Files.concat;
import static com.homeflix.acquisition.support.Mp4Files.ftyp;
import static com.homeflix.acquisition.support.Mp4Files.movie;
import static com.homeflix.acquisition.support.Mp4Files.padded;
import static com.homeflix.acquisition.support.Mp4Files.track;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.homeflix.acquisition.support.Mp4Files;

/** What makes a file a supported MP4 (ADR-0015, R-19), from its structure only. */
class Mp4InspectorTest {

	private static final byte[] FTYP = ftyp("isom", "isom", "avc1");

	@TempDir
	Path folder;

	@Test
	void anMp4WithH264AndAacIsAccepted() throws IOException {
		assertThat(problem(Mp4Files.valid(4096))).isEmpty();
	}

	@Test
	void anMp4WithoutSoundIsAccepted() throws IOException {
		assertThat(problem(padded(2048, FTYP, movie("avc3", null)))).isEmpty();
	}

	@Test
	void theLayoutOfTheDemoFileIsAccepted() throws IOException {
		// sintel.mp4: ftyp isom (iso2, avc1, mp41), moov, free, mdat (docs/media/p2p-test-file.md).
		assertThat(problem(padded(4096, ftyp("isom", "isom", "iso2", "avc1", "mp41"), movie("avc1", "mp4a"),
				box("free")))).isEmpty();
	}

	@Test
	void otherTracksAreAllowed() throws IOException {
		byte[] moov = box("moov", track("vide", "avc1"), track("soun", "mp4a"), track("text", "tx3g"));
		assertThat(problem(padded(4096, FTYP, moov))).isEmpty();
	}

	@Test
	void aLargeBoxSizeIsRead() throws IOException {
		byte[] mdat = concat(ByteBuffer.allocate(4).putInt(1).array(), "mdat".getBytes(),
				ByteBuffer.allocate(8).putLong(16 + 100).array(), new byte[100]);
		assertThat(problem(concat(FTYP, movie("avc1", "mp4a"), mdat))).isEmpty();
	}

	@Test
	void anotherVideoCodecIsRefused() throws IOException {
		assertThat(problem(padded(4096, FTYP, movie("hvc1", "mp4a")))).contains("video track that is not H.264: [hvc1]");
	}

	@Test
	void anotherSoundCodecIsRefused() throws IOException {
		assertThat(problem(padded(4096, FTYP, movie("avc1", "ac-3")))).contains("sound track that is not AAC: [ac-3]");
	}

	@Test
	void aFileWithoutVideoIsRefused() throws IOException {
		assertThat(problem(padded(4096, FTYP, box("moov", track("soun", "mp4a"))))).contains("no video track");
	}

	@Test
	void theFirstBoxMustBeFtypWithAnMp4Brand() throws IOException {
		assertThat(problem(padded(4096, movie("avc1", "mp4a"), FTYP))).contains("the first box is moov, not ftyp");
		assertThat(problem(padded(4096, ftyp("qt  ", "qt  "), movie("avc1", "mp4a")))).contains("no MP4 brand in ftyp");
		assertThat(problem(padded(4096, FTYP, FTYP, movie("avc1", "mp4a")))).contains("a second ftyp box");
	}

	@Test
	void moovAndMdatMustBeThere() throws IOException {
		assertThat(problem(concat(FTYP, box("mdat", new byte[64])))).contains("no moov box");
		assertThat(problem(concat(FTYP, movie("avc1", "mp4a")))).contains("no mdat box");
		assertThat(problem(padded(4096, FTYP, movie("avc1", "mp4a"), movie("avc1", "mp4a"))))
				.contains("a second moov box");
	}

	@Test
	void boxesMustCoverTheFileExactly() throws IOException {
		byte[] valid = Mp4Files.valid(4096);
		// Cut short: the last box no longer fits.
		assertThat(problem(java.util.Arrays.copyOf(valid, 4000))).hasValueSatisfying(p -> assertThat(p).contains("does not fit"));
		// Trailing bytes that are not a box.
		assertThat(problem(concat(valid, new byte[] { 1, 2, 3 }))).hasValueSatisfying(p -> assertThat(p).contains("truncated"));
	}

	@Test
	void aBrokenMovieBoxIsRefused() throws IOException {
		byte[] brokenTrack = box("trak", box("mdia", box("hdlr", new byte[4])));
		assertThat(problem(padded(4096, FTYP, box("moov", brokenTrack)))).contains("truncated hdlr box");
		byte[] noStbl = box("trak", box("mdia", box("hdlr", concat(new byte[8], "vide".getBytes(), new byte[12])),
				box("minf")));
		assertThat(problem(padded(4096, FTYP, box("moov", noStbl)))).contains("0 stbl boxes where one is expected");
	}

	@Test
	void otherKindsOfFilesAreRefused() throws IOException {
		assertThat(problem("plain text that only looks like a video".getBytes())).isPresent();
		assertThat(problem(concat(new byte[] { 0x7f, 'E', 'L', 'F' }, new byte[200]))).isPresent();
		assertThat(problem(concat("PK".getBytes(), new byte[] { 3, 4 }, new byte[200]))).isPresent();
		assertThat(problem(new byte[0])).contains("no moov box");
	}

	private Optional<String> problem(byte[] content) throws IOException {
		Path file = Files.write(folder.resolve("film.mp4"), content);
		return Mp4Inspector.problem(file);
	}

}
