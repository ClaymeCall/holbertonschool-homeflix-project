package com.homeflix.acquisition.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.homeflix.acquisition.config.DownloadsProperties;
import com.homeflix.acquisition.support.Mp4Files;
import com.homeflix.acquisition.verification.DownloadedFileVerifier.Accepted;
import com.homeflix.acquisition.verification.DownloadedFileVerifier.Refused;

/** The checks of a completed download before the handoff (J7; ACQ-06, R-19). */
class DownloadedFileVerifierTest {

	private static final int SIZE = 4096;

	@TempDir
	Path root;

	private Path downloads;
	private DownloadedFileVerifier verifier;

	@BeforeEach
	void setUp() throws IOException {
		downloads = Files.createDirectory(root.resolve("downloads"));
		verifier = new DownloadedFileVerifier(new DownloadsProperties(downloads.toString(), Duration.ofMinutes(5)));
	}

	@Test
	void aSupportedFileOfTheExpectedSizeIsAccepted() throws IOException {
		Files.write(downloads.resolve("film.mp4"), Mp4Files.valid(SIZE));

		assertThat(verifier.verify("/downloads/", "/downloads/film.mp4", SIZE)).isEqualTo(new Accepted("film.mp4"));
		assertThat(verifier.verify("/downloads", "/downloads/film.mp4", SIZE)).isEqualTo(new Accepted("film.mp4"));
	}

	@Test
	void aFileInASubfolderKeepsItsRelativePath() throws IOException {
		Files.createDirectory(downloads.resolve("Sintel"));
		Files.write(downloads.resolve("Sintel/film.MP4"), Mp4Files.valid(SIZE));

		assertThat(verifier.verify("/downloads/", "/downloads/Sintel/film.MP4", SIZE))
				.isEqualTo(new Accepted("Sintel/film.MP4"));
	}

	@Test
	void aDifferentSizeIsRefused() throws IOException {
		Files.write(downloads.resolve("film.mp4"), Mp4Files.valid(SIZE));

		assertThat(verifier.verify("/downloads/", "/downloads/film.mp4", SIZE + 1))
				.isEqualTo(new Refused("film.mp4 is 4096 bytes, 4097 expected"));
	}

	@Test
	void aFileThatIsNotAnMp4IsRefusedEvenWithTheRightNameAndSize() throws IOException {
		Files.write(downloads.resolve("film.mp4"), new byte[SIZE]);

		assertThat(verifier.verify("/downloads/", "/downloads/film.mp4", SIZE)).isInstanceOf(Refused.class);
	}

	@Test
	void anotherExtensionIsRefused() throws IOException {
		Files.write(downloads.resolve("film.mkv"), Mp4Files.valid(SIZE));
		Files.write(downloads.resolve("film.mp4.exe"), Mp4Files.valid(SIZE));

		assertThat(verifier.verify("/downloads/", "/downloads/film.mkv", SIZE))
				.isEqualTo(new Refused("film.mkv is not an .mp4 file"));
		assertThat(verifier.verify("/downloads/", "/downloads/film.mp4.exe", SIZE)).isInstanceOf(Refused.class);
	}

	@Test
	void aTorrentOfSeveralFilesIsRefused() throws IOException {
		Files.createDirectory(downloads.resolve("Sintel"));
		Files.write(downloads.resolve("Sintel/film.mp4"), Mp4Files.valid(SIZE));

		assertThat(verifier.verify("/downloads/", "/downloads/Sintel", SIZE)).isInstanceOf(Refused.class);
	}

	@Test
	void aMissingFileIsRefused() {
		assertThat(verifier.verify("/downloads/", "/downloads/film.mp4", SIZE)).isInstanceOf(Refused.class);
	}

	@Test
	void aPathOutsideTheDownloadsFolderIsRefused() throws IOException {
		Files.write(root.resolve("outside.mp4"), Mp4Files.valid(SIZE));

		for (String content : new String[] { "/downloads/../outside.mp4", "/etc/passwd", "/downloadsX/film.mp4",
				"/downloads/", "/downloads//film.mp4", "/downloads/./film.mp4", "/downloads/a\\..\\film.mp4" }) {
			assertThat(verifier.verify("/downloads/", content, SIZE)).as(content).isInstanceOf(Refused.class);
		}
		assertThat(verifier.verify("/downloads/", null, SIZE)).isInstanceOf(Refused.class);
		assertThat(verifier.verify(null, "/downloads/film.mp4", SIZE)).isInstanceOf(Refused.class);
	}

	@Test
	void aSymbolicLinkIsRefused() throws IOException {
		Path target = Files.write(root.resolve("outside.mp4"), Mp4Files.valid(SIZE));
		Files.createSymbolicLink(downloads.resolve("film.mp4"), target);
		Path inside = Files.write(downloads.resolve("real.mp4"), Mp4Files.valid(SIZE));
		Files.createSymbolicLink(downloads.resolve("link.mp4"), inside);

		assertThat(verifier.verify("/downloads/", "/downloads/film.mp4", SIZE)).isInstanceOf(Refused.class);
		assertThat(verifier.verify("/downloads/", "/downloads/link.mp4", SIZE)).isInstanceOf(Refused.class);
	}

	@Test
	void aSubfolderThatIsALinkOutsideIsRefused() throws IOException {
		Path outside = Files.createDirectory(root.resolve("elsewhere"));
		Files.write(outside.resolve("film.mp4"), Mp4Files.valid(SIZE));
		Files.createSymbolicLink(downloads.resolve("Sintel"), outside);

		assertThat(verifier.verify("/downloads/", "/downloads/Sintel/film.mp4", SIZE)).isInstanceOf(Refused.class);
	}

}
