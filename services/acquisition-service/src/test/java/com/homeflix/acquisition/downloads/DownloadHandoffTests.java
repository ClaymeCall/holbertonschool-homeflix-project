package com.homeflix.acquisition.downloads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.ObjectMapper;

import com.homeflix.acquisition.HeaderCallers;
import com.homeflix.acquisition.TestcontainersConfiguration;
import com.homeflix.acquisition.support.LibraryStub;
import com.homeflix.acquisition.support.Mp4Files;
import com.homeflix.acquisition.support.QbittorrentContainer;
import com.homeflix.acquisition.torrent.QbittorrentClient;
import com.homeflix.acquisition.torrent.QbittorrentClient.Torrent;

/**
 * Completed-file checks and handoff to the library (J7; ACQ-06, ACQ-07, R-19, R-22; consistency rules,
 * "Completing" and "Time limit and visible failure"), against a real PostgreSQL and a real qBittorrent. The
 * client reports the torrent as complete, with a file the test writes in a temporary downloads folder.
 */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class DownloadHandoffTests {

	private static final int SIZE = 4096;
	private static final String TOKEN = "test-token-0123456789abcdef0123456789abcdef";
	private static final LibraryStub LIBRARY = new LibraryStub();
	private static final Path DOWNLOADS = temporaryFolder();

	@DynamicPropertySource
	static void dependencies(DynamicPropertyRegistry registry) {
		registry.add("homeflix.library.base-url", LIBRARY::url);
		registry.add("homeflix.qbittorrent.url", QbittorrentContainer::url);
		registry.add("homeflix.qbittorrent.username", () -> QbittorrentContainer.USERNAME);
		registry.add("homeflix.qbittorrent.password", () -> QbittorrentContainer.PASSWORD);
		registry.add("homeflix.downloads.folder", DOWNLOADS::toString);
	}

	@AfterAll
	static void stopLibrary() {
		LIBRARY.stop();
	}

	@Autowired
	private DownloadTracker tracker;
	@Autowired
	private DownloadService downloads;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private WebApplicationContext context;
	@MockitoSpyBean
	private QbittorrentClient torrents;
	/** The real repository, unless a test interleaves a cancel. */
	@MockitoSpyBean
	private DownloadRepository repository;

	private final ObjectMapper json = new ObjectMapper();
	private UUID entryId;
	private String hash;
	private String fileName;

	@BeforeEach
	void setUp() {
		clean();
		LIBRARY.reset();
		hash = randomHash();
		fileName = "film-" + hash.substring(0, 8) + ".mp4";
		entryId = insertEntry(hash);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from download_job");
		jdbc.update("delete from acquisition_entry where source_name = 'Test source'");
	}

	// ---- Handoff (ACQ-07) ----

	@Test
	void aVerifiedFileIsHandedToTheLibraryAndTheDownloadCompletes() throws Exception {
		UUID jobId = start();
		write(Mp4Files.valid(SIZE));
		reportComplete();

		tracker.check();

		Download download = downloads.get(jobId);
		assertThat(download.state()).isEqualTo(DownloadState.COMPLETED);
		assertThat(download.progressPercent()).isEqualTo(100);
		assertThat(download.finishedAt()).isNotNull();
		assertThat(download.errorMessage()).isNull();
		Map<String, Object> row = row(jobId);
		assertThat(row.get("completed_relative_path")).isEqualTo(fileName);
		assertThat(row.get("library_synced_at")).isNotNull();
		assertThat(LIBRARY.calls()).singleElement().satisfies(call -> {
			assertThat(call.method()).isEqualTo("POST");
			assertThat(call.path()).isEqualTo("/internal/downloads/" + entryId + "/completed");
			assertThat(call.token()).isEqualTo(TOKEN);
			assertThat(json.readTree(call.body()).get("relativePath").asString()).isEqualTo(fileName);
		});
		// The file and the torrent stay: the library plays the file, the client seeds it (R-16).
		assertThat(Files.exists(DOWNLOADS.resolve(fileName))).isTrue();
		assertThat(QbittorrentContainer.hasTorrent(hash)).isTrue();

		// A completed download is not handed over again.
		LIBRARY.reset();
		tracker.check();
		assertThat(LIBRARY.calls()).isEmpty();
	}

	@Test
	void theHandoffIsTriedAgainUntilTheLibraryAccepts() throws Exception {
		UUID jobId = start();
		write(Mp4Files.valid(SIZE));
		reportComplete();
		LIBRARY.answerCompletedWith(503);

		tracker.check();
		tracker.check();

		// Waits at 100 % while the library does not answer: the screen can show it as finishing.
		assertThat(row(jobId).get("state")).isEqualTo("DOWNLOADING");
		assertThat(row(jobId).get("progress_percent")).isEqualTo(100);
		assertThat(row(jobId).get("handoff_started_at")).isNotNull();
		assertThat(LIBRARY.calls()).hasSize(2);

		LIBRARY.answerCompletedWith(200);
		tracker.check();
		assertThat(row(jobId).get("state")).isEqualTo("COMPLETED");
	}

	@Test
	void aHandoffThatOutlastsTheTimeLimitFailsVisiblyAndKeepsTheFile() throws Exception {
		UUID jobId = start();
		write(Mp4Files.valid(SIZE));
		reportComplete();
		LIBRARY.answerCompletedWith(503);
		tracker.check();

		jdbc.update("update download_job set handoff_started_at = now() - interval '6 minutes' where id = ?", jobId);
		LIBRARY.answerFailedWith(204);
		tracker.check();

		Download download = downloads.get(jobId);
		assertThat(download.state()).isEqualTo(DownloadState.FAILED);
		assertThat(download.errorCode()).isEqualTo("handoff_failed");
		assertThat(download.errorMessage())
				.isEqualTo("The video was downloaded but could not be added to the library. You can start it again.");
		assertThat(LIBRARY.calls()).extracting(LibraryStub.Call::method, LibraryStub.Call::path).endsWith(
				tuple("POST", "/internal/downloads/" + entryId + "/failed"));
		// The verified file is kept, so starting again hands it over without downloading it again.
		assertThat(Files.exists(DOWNLOADS.resolve(fileName))).isTrue();
		assertThat(QbittorrentContainer.hasTorrent(hash)).isTrue();
	}

	@Test
	void aVerifiedFileWhoseTorrentIsGoneIsStillHandedOver() throws Exception {
		UUID jobId = start();
		write(Mp4Files.valid(SIZE));
		reportComplete();
		LIBRARY.answerCompletedWith(503);
		tracker.check();

		// The client no longer lists the torrent: the file is enough for the handoff.
		doReturn(List.of()).when(torrents).list();
		jdbc.update("update download_job set created_at = now() - interval '2 minutes' where id = ?", jobId);
		LIBRARY.answerCompletedWith(200);
		tracker.check();

		assertThat(row(jobId).get("state")).isEqualTo("COMPLETED");
	}

	// ---- Checks of the completed file (ACQ-06, R-19) ----

	@Test
	void aFileOfAnotherSizeFailsTheChecksAndIsDeleted() throws Exception {
		UUID jobId = start();
		write(Mp4Files.valid(SIZE + 512));
		reportComplete();

		tracker.check();

		assertVerificationFailed(jobId);
	}

	@Test
	void aFileThatIsNotAnMp4FailsTheChecksAndIsDeleted() throws Exception {
		UUID jobId = start();
		write(new byte[SIZE]);
		reportComplete();

		tracker.check();

		assertVerificationFailed(jobId);
	}

	@Test
	void aFileOutsideTheDownloadsFolderFailsTheChecks() throws Exception {
		UUID jobId = start();
		reportComplete("/etc/passwd");

		tracker.check();

		assertVerificationFailed(jobId);
	}

	@Test
	void aFileSavedInASubfolderIsHandedOverWithItsPathFromTheDownloadsFolder() throws Exception {
		UUID jobId = start();
		Files.createDirectories(DOWNLOADS.resolve("movies"));
		Files.write(DOWNLOADS.resolve("movies").resolve(fileName), Mp4Files.valid(SIZE));
		reportComplete(DOWNLOADS + "/movies/" + fileName);

		tracker.check();

		assertThat(row(jobId).get("state")).isEqualTo("COMPLETED");
		assertThat(row(jobId).get("completed_relative_path")).isEqualTo("movies/" + fileName);
	}

	@Test
	void aFileThatCannotBeReadIsKeptAndCheckedAgain() throws Exception {
		UUID jobId = start();
		Path file = write(Mp4Files.valid(SIZE));
		reportComplete();
		// A permission problem, such as a user mismatch between the torrent client and this service.
		Files.setPosixFilePermissions(file, Set.of());

		tracker.check();

		assertThat(row(jobId).get("state")).isEqualTo("DOWNLOADING");
		assertThat(row(jobId).get("completed_relative_path")).isNull();
		assertThat(QbittorrentContainer.hasTorrent(hash)).isTrue();
		assertThat(Files.exists(file)).isTrue();
		assertThat(LIBRARY.calls()).isEmpty();

		Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		tracker.check();
		assertThat(row(jobId).get("state")).isEqualTo("COMPLETED");
	}

	@Test
	void aDownloadCancelledWhileItsFileIsCheckedIsNotHandedOver() throws Exception {
		UUID jobId = start();
		write(Mp4Files.valid(SIZE));
		reportComplete();
		// The cancel comes after the checks, just before the job is marked verified.
		doAnswer(call -> {
			downloads.cancel(jobId);
			return call.callRealMethod();
		}).when(repository).markVerified(eq(jobId), any());

		tracker.check();

		assertThat(row(jobId).get("error_code")).isEqualTo("cancelled");
		assertThat(row(jobId).get("completed_relative_path")).isNull();
		assertThat(LIBRARY.calls()).extracting(LibraryStub.Call::method, LibraryStub.Call::path)
				.containsExactly(tuple("POST", "/internal/downloads/" + entryId + "/failed"));
	}

	@Test
	void aDownloadBeingHandedOverCannotBeCancelled() throws Exception {
		UUID jobId = start();
		write(Mp4Files.valid(SIZE));
		reportComplete();
		LIBRARY.answerCompletedWith(503);
		tracker.check();

		MockMvcBuilders.webAppContextSetup(context).build()
				.perform(post("/downloads/" + jobId + "/cancel").header("X-Test-Caller", "admin"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("download_not_cancellable"));
		assertThat(Files.exists(DOWNLOADS.resolve(fileName))).isTrue();
	}

	// ---- Helpers ----

	private void assertVerificationFailed(UUID jobId) {
		Download download = downloads.get(jobId);
		assertThat(download.state()).isEqualTo(DownloadState.FAILED);
		assertThat(download.errorCode()).isEqualTo("verification_failed");
		assertThat(download.errorMessage())
				.isEqualTo("The downloaded file is not the expected video, so it was deleted. You can start it again.");
		assertThat(row(jobId).get("completed_relative_path")).isNull();
		// The torrent and its data are removed from the client, and nothing is handed over.
		assertThat(QbittorrentContainer.hasTorrent(hash)).isFalse();
		assertThat(LIBRARY.calls()).extracting(LibraryStub.Call::method, LibraryStub.Call::path)
				.containsExactly(tuple("POST", "/internal/downloads/" + entryId + "/failed"));
	}

	private UUID start() {
		UUID jobId = downloads.start(entryId, UUID.randomUUID()).id();
		LIBRARY.reset();
		return jobId;
	}

	private Path write(byte[] content) throws IOException {
		return Files.write(DOWNLOADS.resolve(fileName), content);
	}

	private void reportComplete() {
		reportComplete(DOWNLOADS + "/" + fileName);
	}

	/**
	 * Makes the client report the test's torrent as complete, with this content path as the client sees it:
	 * the downloads folder is mounted here at the same path.
	 */
	private void reportComplete(String contentPath) {
		List<Torrent> reported = torrents.list().stream()
				.map(t -> t.hash().equals(hash) ? new Torrent(t.hash(), "stalledUP", 1, t.tags(), contentPath) : t)
				.toList();
		doReturn(reported).when(torrents).list();
	}

	private Map<String, Object> row(UUID jobId) {
		return jdbc.queryForMap("select * from download_job where id = ?", jobId);
	}

	private UUID insertEntry(String infoHash) {
		return jdbc.queryForObject("""
				insert into acquisition_entry (title, source_name, source_url, licence, licence_url,
				    download_source, expected_size_bytes, expected_format)
				values ('Handed over film', 'Test source', 'https://example.org/', 'Test licence',
				    'https://example.org/licence', ?, ?, 'MP4')
				returning id
				""", UUID.class, "magnet:?xt=urn:btih:" + infoHash + "&dn=test.mp4", SIZE);
	}

	private static Path temporaryFolder() {
		try {
			return Files.createTempDirectory("homeflix-downloads");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String randomHash() {
		byte[] bytes = new byte[20];
		ThreadLocalRandom.current().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

}
