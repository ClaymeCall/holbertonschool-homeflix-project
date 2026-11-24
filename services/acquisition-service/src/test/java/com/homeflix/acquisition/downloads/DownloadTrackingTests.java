package com.homeflix.acquisition.downloads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.homeflix.acquisition.TestcontainersConfiguration;
import com.homeflix.acquisition.support.LibraryStub;
import com.homeflix.acquisition.support.QbittorrentContainer;
import com.homeflix.acquisition.torrent.QbittorrentClient;
import com.homeflix.acquisition.torrent.QbittorrentClient.Torrent;
import com.homeflix.acquisition.web.ServiceUnavailableException;

/**
 * The status check of downloads (J6; ACQ-03, ACQ-04) against a real PostgreSQL, a real qBittorrent and a
 * stand-in for the library. The periodic check is off in tests: each test calls {@link DownloadTracker#check()}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DownloadTrackingTests {

	private static final LibraryStub LIBRARY = new LibraryStub();

	@DynamicPropertySource
	static void dependencies(DynamicPropertyRegistry registry) {
		registry.add("homeflix.library.base-url", LIBRARY::url);
		registry.add("homeflix.qbittorrent.url", QbittorrentContainer::url);
		registry.add("homeflix.qbittorrent.username", () -> QbittorrentContainer.USERNAME);
		registry.add("homeflix.qbittorrent.password", () -> QbittorrentContainer.PASSWORD);
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
	/** The real client, unless a test makes it report a state or fail. */
	@MockitoSpyBean
	private QbittorrentClient torrents;
	/** The real repository, unless a test makes it fail. */
	@MockitoSpyBean
	private DownloadRepository repository;

	private UUID entryId;
	private String hash;

	@BeforeEach
	void setUp() {
		clean();
		LIBRARY.reset();
		hash = randomHash();
		entryId = insertEntry("Tracked film", hash);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from download_job");
		jdbc.update("delete from acquisition_entry where source_name = 'Test source'");
	}

	// ---- State and progress (ACQ-04) ----

	@Test
	void aJobTakesTheStateAndProgressOfItsTorrent() {
		UUID jobId = start();

		// A magnet link without peers fetches its metadata: nothing transfers yet.
		tracker.check();
		assertThat(stateOf(jobId)).isEqualTo("QUEUED");

		reportTorrent("downloading", 0.4237);
		tracker.check();
		Map<String, Object> job = row(jobId);
		assertThat(job.get("state")).isEqualTo("DOWNLOADING");
		assertThat(job.get("progress_percent")).isEqualTo(42);

		// Complete in the client: stays DOWNLOADING at 100 % until the handoff (J7).
		reportTorrent("stalledUP", 1);
		tracker.check();
		job = row(jobId);
		assertThat(job.get("state")).isEqualTo("DOWNLOADING");
		assertThat(job.get("progress_percent")).isEqualTo(100);
		assertThat(job.get("finished_at")).isNull();
	}

	@Test
	void anUnknownStateLeavesTheJobAsItIs() {
		UUID jobId = start();
		reportTorrent("downloading", 0.5);
		tracker.check();

		reportTorrent("aStateOfALaterVersion", 0.9);
		tracker.check();

		assertThat(stateOf(jobId)).isEqualTo("DOWNLOADING");
		assertThat(row(jobId).get("progress_percent")).isEqualTo(50);
	}

	// ---- Failures, in plain language (ACQ-03) ----

	@Test
	void aTorrentInErrorFailsTheJobRemovesTheTorrentAndTellsTheLibrary() {
		UUID jobId = start();
		reportTorrent("error", 0.3);

		tracker.check();

		Download download = downloads.get(jobId);
		assertThat(download.state()).isEqualTo(DownloadState.FAILED);
		assertThat(download.errorCode()).isEqualTo("client_error");
		assertThat(download.errorMessage())
				.isEqualTo("The download service could not download this title. You can start it again.");
		assertThat(download.finishedAt()).isNotNull();
		assertThat(QbittorrentContainer.hasTorrent(hash)).isFalse();
		assertLibraryToldItFailed();
	}

	@Test
	void aTorrentRemovedOutsideHomeFlixFailsTheJobAfterTheGracePeriod() {
		UUID jobId = start();
		QbittorrentContainer.removeTorrent(hash);

		// Within the grace period, a start may still be adding the torrent: nothing changes.
		tracker.check();
		assertThat(stateOf(jobId)).isEqualTo("QUEUED");

		jdbc.update("update download_job set created_at = now() - interval '2 minutes' where id = ?", jobId);
		tracker.check();

		Download download = downloads.get(jobId);
		assertThat(download.state()).isEqualTo(DownloadState.FAILED);
		assertThat(download.errorCode()).isEqualTo("torrent_missing");
		assertThat(download.errorMessage())
				.isEqualTo("This download is no longer in the download service. You can start it again.");
		assertLibraryToldItFailed();
	}

	@Test
	void aCancelledJobIsNotChangedByTheCheck() {
		UUID jobId = start();
		reportTorrent("downloading", 0.5);
		downloads.cancel(jobId);

		tracker.check();

		Download download = downloads.get(jobId);
		assertThat(download.errorCode()).isEqualTo("cancelled");
		assertThat(download.progressPercent()).isZero();
	}

	// ---- Library notifications (consistency rules, "Failing and cancelling") ----

	@Test
	void aFailureTheLibraryCouldNotBeToldIsToldAtTheNextCheck() {
		UUID jobId = start();
		LIBRARY.answerFailedWith(503);
		downloads.cancel(jobId);
		assertThat(syncedOf(jobId)).isFalse();

		tracker.check();
		assertThat(syncedOf(jobId)).isFalse();

		LIBRARY.answerFailedWith(204);
		tracker.check();
		assertThat(syncedOf(jobId)).isTrue();
	}

	@Test
	void theFailureOfAnOlderJobIsNotToldWhileANewerJobDownloads() {
		// Job A is cancelled while the library is down, then the entry is started again (job B).
		UUID older = start();
		LIBRARY.answerFailedWith(503);
		downloads.cancel(older);
		LIBRARY.reset();
		start();

		tracker.check();

		// Telling the library about A now would make the entry UNAVAILABLE while B downloads.
		assertThat(LIBRARY.calls()).isEmpty();
		assertThat(syncedOf(older)).isTrue();
	}

	@Test
	void aFailureOfAnEntryTheLibraryNoLongerHasIsNotRepeated() {
		UUID jobId = start();
		// The library answers 404 when an administrator removed the entry (H8).
		LIBRARY.answerFailedWith(404);
		downloads.cancel(jobId);

		assertThat(syncedOf(jobId)).isTrue();
		LIBRARY.reset();
		tracker.check();
		assertThat(LIBRARY.calls()).isEmpty();
	}

	// ---- Torrents left behind ----

	@Test
	void theTorrentOfAFailedJobIsRemovedButNotOthers() {
		UUID failedJob = insertJob("FAILED", hash);
		QbittorrentContainer.addTorrent("magnet:?xt=urn:btih:" + hash, "homeflix-job-" + failedJob);

		String completedHash = randomHash();
		UUID completedJob = insertJob("COMPLETED", completedHash);
		QbittorrentContainer.addTorrent("magnet:?xt=urn:btih:" + completedHash, "homeflix-job-" + completedJob);

		String notOurs = randomHash();
		QbittorrentContainer.addTorrent("magnet:?xt=urn:btih:" + notOurs);

		tracker.check();

		assertThat(QbittorrentContainer.hasTorrent(hash)).isFalse();
		assertThat(QbittorrentContainer.hasTorrent(completedHash)).isTrue();
		assertThat(QbittorrentContainer.hasTorrent(notOurs)).isTrue();
		QbittorrentContainer.removeTorrent(completedHash);
		QbittorrentContainer.removeTorrent(notOurs);
	}

	@Test
	void aTorrentTakenOverByANewStartIsKept() {
		UUID oldJob = insertJob("FAILED", hash);
		QbittorrentContainer.addTorrent("magnet:?xt=urn:btih:" + hash, "homeflix-job-" + oldJob);

		UUID newJob = start();
		tracker.check();

		assertThat(QbittorrentContainer.hasTorrent(hash)).isTrue();
		assertThat(stateOf(newJob)).isEqualTo("QUEUED");
	}

	@Test
	void aTorrentTakenOverByANewStartIsKeptOnceThatStartIsComplete() {
		// The new start keeps the torrent left by the failed job, with the failed job's tag.
		UUID oldJob = insertJob("FAILED", hash);
		QbittorrentContainer.addTorrent("magnet:?xt=urn:btih:" + hash, "homeflix-job-" + oldJob);
		UUID newJob = start();
		assertThat(QbittorrentContainer.torrentInfo(hash)).contains("homeflix-job-" + oldJob);
		jdbc.update("update download_job set state = 'COMPLETED', progress_percent = 100, finished_at = now() where id = ?",
				newJob);

		tracker.check();

		assertThat(QbittorrentContainer.hasTorrent(hash)).isTrue();
	}

	@Test
	void aStartBetweenTheCheckAndTheRemovalKeepsItsTorrent() throws Exception {
		// The torrent of a failed job is about to be removed when a new start of the same entry takes it over.
		UUID failedJob = insertJob("FAILED", hash);
		QbittorrentContainer.addTorrent("magnet:?xt=urn:btih:" + hash, "homeflix-job-" + failedJob);
		CountDownLatch secondStarted = new CountDownLatch(1);
		CompletableFuture<UUID> second = new CompletableFuture<>();
		doAnswer(call -> {
			CompletableFuture.runAsync(() -> {
				try {
					second.complete(downloads.start(entryId, UUID.randomUUID()).id());
				}
				catch (RuntimeException ex) {
					second.completeExceptionally(ex);
				}
				secondStarted.countDown();
			});
			// Gives the new start the time to take the torrent over, if nothing holds it back.
			secondStarted.await(500, TimeUnit.MILLISECONDS);
			return call.callRealMethod();
		}).when(torrents).delete(hash);

		tracker.check();

		UUID newJob = second.get(5, TimeUnit.SECONDS);
		assertThat(stateOf(newJob)).isEqualTo("QUEUED");
		assertThat(QbittorrentContainer.hasTorrent(hash)).isTrue();
	}

	// ---- Errors ----

	@Test
	void anErrorOnOneJobDoesNotStopTheCheckOfTheOthers() {
		UUID broken = start();
		String otherHash = randomHash();
		entryId = insertEntry("Another tracked film", otherHash);
		UUID other = start();
		doThrow(new DataAccessResourceFailureException("unexpected")).when(repository)
				.updateStatus(eq(broken), any(), anyInt());
		List<Torrent> real = torrents.list();
		doReturn(real.stream().map(t -> new Torrent(t.hash(), "downloading", 0.5, t.tags())).toList())
				.when(torrents).list();

		tracker.check();

		assertThat(stateOf(other)).isEqualTo("DOWNLOADING");
		assertThat(stateOf(broken)).isEqualTo("QUEUED");
		QbittorrentContainer.removeTorrent(otherHash);
	}

	// ---- Torrent client down ----

	@Test
	void aTorrentClientThatDoesNotAnswerChangesNoJob() {
		UUID jobId = start();
		jdbc.update("update download_job set created_at = now() - interval '2 minutes' where id = ?", jobId);
		doThrow(new ServiceUnavailableException("Torrent client unavailable", null)).when(torrents).list();

		tracker.check();

		assertThat(stateOf(jobId)).isEqualTo("QUEUED");
	}

	// ---- Helpers ----

	private UUID start() {
		UUID jobId = downloads.start(entryId, UUID.randomUUID()).id();
		LIBRARY.reset();
		return jobId;
	}

	/** Makes the client report this state and progress for the test's torrent, the others as they are. */
	private void reportTorrent(String state, double progress) {
		List<Torrent> real = torrents.list();
		List<Torrent> reported = real.stream()
				.map(t -> t.hash().equals(hash) ? new Torrent(t.hash(), state, progress, t.tags()) : t)
				.toList();
		doReturn(reported).when(torrents).list();
	}

	private void assertLibraryToldItFailed() {
		assertThat(LIBRARY.calls()).extracting(LibraryStub.Call::method, LibraryStub.Call::path)
				.containsExactly(tuple("POST", "/internal/downloads/" + entryId + "/failed"));
	}

	private Map<String, Object> row(UUID jobId) {
		return jdbc.queryForMap("select * from download_job where id = ?", jobId);
	}

	private String stateOf(UUID jobId) {
		return (String) row(jobId).get("state");
	}

	private boolean syncedOf(UUID jobId) {
		return row(jobId).get("library_synced_at") != null;
	}

	private UUID insertJob(String state, String clientHash) {
		return jdbc.queryForObject("""
				insert into download_job (entry_id, client_hash, state, started_by, error_code, finished_at,
				    library_synced_at)
				values (?, ?, ?, ?, case when ? = 'FAILED' then 'client_error' end, now(), now())
				returning id
				""", UUID.class, entryId, clientHash, state, UUID.randomUUID(), state);
	}

	private UUID insertEntry(String title, String infoHash) {
		return jdbc.queryForObject("""
				insert into acquisition_entry (title, source_name, source_url, licence, licence_url,
				    download_source, expected_size_bytes, expected_format)
				values (?, 'Test source', 'https://example.org/', 'Test licence',
				    'https://example.org/licence', ?, 1000, 'MP4')
				returning id
				""", UUID.class, title, "magnet:?xt=urn:btih:" + infoHash + "&dn=test.mp4");
	}

	private static String randomHash() {
		byte[] bytes = new byte[20];
		ThreadLocalRandom.current().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

}
