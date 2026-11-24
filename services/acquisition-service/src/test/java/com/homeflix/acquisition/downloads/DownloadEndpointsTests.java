package com.homeflix.acquisition.downloads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.acquisition.HeaderCallers;
import com.homeflix.acquisition.TestcontainersConfiguration;
import com.homeflix.acquisition.library.LibraryClient;
import com.homeflix.acquisition.support.LibraryStub;
import com.homeflix.acquisition.support.QbittorrentContainer;
import com.homeflix.acquisition.torrent.QbittorrentClient;
import com.homeflix.acquisition.web.ServiceUnavailableException;

import tools.jackson.databind.ObjectMapper;

/**
 * Starting and cancelling downloads (J5; ACQ-03, ACQ-05, ACQ-09, ACQ-10, ACQ-11) against a real PostgreSQL,
 * a real qBittorrent and a stand-in for the library's internal endpoints (consistency rules).
 */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class DownloadEndpointsTests {

	private static final String TOKEN = "test-token-0123456789abcdef0123456789abcdef";
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
	private WebApplicationContext context;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private DownloadService downloads;
	/** The real client, unless a test makes it fail or interleaves a cancel. */
	@MockitoSpyBean
	private QbittorrentClient torrents;
	/** The real client, unless a test holds back a call. */
	@MockitoSpyBean
	private LibraryClient library;

	private final ObjectMapper json = new ObjectMapper();
	private MockMvc mvc;
	private UUID entryId;
	private String entryHash;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		clean();
		LIBRARY.reset();
		entryHash = randomHash();
		entryId = insertEntry("Test Film", entryHash);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from download_job");
		jdbc.update("delete from acquisition_entry where source_name = 'Test source'");
	}

	// ---- Permissions (ACQ-05, SEC-02) ----

	@Test
	void anonymousCallerCannotStartCancelOrRead() throws Exception {
		mvc.perform(start(entryId)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("unauthenticated"));
		mvc.perform(post("/downloads/" + UUID.randomUUID() + "/cancel")).andExpect(status().isUnauthorized());
		mvc.perform(get("/downloads/" + UUID.randomUUID())).andExpect(status().isUnauthorized());

		assertNothingStarted();
	}

	@Test
	void ordinaryUserCannotStartCancelOrRead() throws Exception {
		as("user", start(entryId)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("forbidden"));
		as("user", post("/downloads/" + UUID.randomUUID() + "/cancel")).andExpect(status().isForbidden());
		as("user", get("/downloads/" + UUID.randomUUID())).andExpect(status().isForbidden());

		assertNothingStarted();
	}

	/** A caller who is not an administrator learns nothing about the rules of the body or the path. */
	@Test
	void permissionIsCheckedBeforeTheBodyAndThePath() throws Exception {
		as("user", post("/downloads").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		as("user", post("/downloads").contentType(MediaType.APPLICATION_JSON).content("not json"))
				.andExpect(status().isForbidden());
		as("user", post("/downloads").contentType(MediaType.TEXT_PLAIN).content("x"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/downloads").contentType(MediaType.APPLICATION_JSON).content("not json"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/downloads/not-an-id/cancel")).andExpect(status().isUnauthorized());
		as("user", get("/downloads/not-an-id")).andExpect(status().isForbidden());

		assertNothingStarted();
	}

	// ---- Validation (SEC-01, ACQ-11) ----

	@Test
	void theRightsReminderMustBeAcknowledged() throws Exception {
		for (String body : new String[] { "{\"entryId\":\"" + entryId + "\"}",
				"{\"entryId\":\"" + entryId + "\",\"rightsAcknowledged\":false}" }) {
			as("admin", post("/downloads").contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("validation_failed"))
					.andExpect(jsonPath("$.fields.rightsAcknowledged")
							.value("Confirm that you have read the reminder about rights before downloading."));
		}
		assertNothingStarted();
	}

	@Test
	void anEntryMustBeChosen() throws Exception {
		as("admin", post("/downloads").contentType(MediaType.APPLICATION_JSON).content("{\"rightsAcknowledged\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.entryId").value("Choose a title from the catalogue."));
		assertNothingStarted();
	}

	@Test
	void aBodyThatIsNotJsonIsRefused() throws Exception {
		as("admin", post("/downloads").contentType(MediaType.APPLICATION_JSON).content("not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"));
		assertNothingStarted();
	}

	@Test
	void anUnknownEntryIsNotFound() throws Exception {
		as("admin", start(UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));
		assertNothingStarted();
	}

	// ---- Start (consistency rules, "Starting") ----

	@Test
	void startTellsTheLibraryFirstThenAddsTheTorrent() throws Exception {
		MvcResult result = as("admin", start(entryId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.entryId").value(entryId.toString()))
				.andExpect(jsonPath("$.state").value("QUEUED"))
				.andExpect(jsonPath("$.progressPercent").value(0))
				.andExpect(jsonPath("$.errorCode").value(nullValue()))
				.andExpect(jsonPath("$.startedBy").doesNotExist())
				.andExpect(jsonPath("$.clientHash").doesNotExist())
				.andReturn();
		UUID jobId = idOf(result);

		assertThat(LIBRARY.calls()).singleElement().satisfies(call -> {
			assertThat(call.method()).isEqualTo("PUT");
			assertThat(call.path()).isEqualTo("/internal/downloads/" + entryId);
			assertThat(call.token()).isEqualTo(TOKEN);
			assertThat(json.readTree(call.body()).get("title").asString()).isEqualTo("Test Film");
		});
		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isTrue();
		assertThat(QbittorrentContainer.torrentInfo(entryHash)).contains("homeflix-job-" + jobId);

		Map<String, Object> row = jdbc.queryForMap("select * from download_job where id = ?", jobId);
		assertThat(row.get("client_hash")).isEqualTo(entryHash);
		assertThat(row.get("started_by")).isNotNull();
		assertThat(row.get("library_synced_at")).isNotNull();
	}

	@Test
	void onlyTheEntrysOwnSourceIsDownloadedWhateverTheBodySays() throws Exception {
		String otherHash = randomHash();
		String body = "{\"entryId\":\"" + entryId + "\",\"rightsAcknowledged\":true,"
				+ "\"downloadSource\":\"magnet:?xt=urn:btih:" + otherHash + "\",\"url\":\"https://evil.example/x.torrent\"}";

		as("admin", post("/downloads").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isTrue();
		assertThat(QbittorrentContainer.hasTorrent(otherHash)).isFalse();
	}

	@Test
	void aSecondStartWhileOneIsActiveIsRefused() throws Exception {
		as("admin", start(entryId)).andExpect(status().isCreated());

		as("admin", start(entryId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("download_in_progress"))
				.andExpect(jsonPath("$.message").value("A download of this title is already in progress."));
		assertThat(LIBRARY.calls()).hasSize(1);
		assertThat(countJobs()).isEqualTo(1);
	}

	@Test
	void aTitleAlreadyInTheLibraryIsRefused() throws Exception {
		LIBRARY.answerStartWith(409);

		as("admin", start(entryId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("already_in_library"))
				.andExpect(jsonPath("$.message").value("This title is already in the library."));
		assertThat(countJobs()).isZero();
		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isFalse();
	}

	@Test
	void anotherConflictFromTheLibraryIsNotShownAsAlreadyInTheLibrary() throws Exception {
		LIBRARY.answerStartWithConflict("something_else");

		as("admin", start(entryId))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"));
		assertThat(countJobs()).isZero();
		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isFalse();
	}

	@Test
	void aTorrentLeftInTheClientByAnEarlierStartIsKept() throws Exception {
		// qBittorrent answers 409 to an add of a torrent it already has.
		QbittorrentContainer.addTorrent("magnet:?xt=urn:btih:" + entryHash);

		UUID jobId = startedJob();

		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isTrue();
		assertThat(jdbc.queryForObject("select state from download_job where id = ?", String.class, jobId))
				.isEqualTo("QUEUED");
	}

	@Test
	void nothingStartsWhenTheLibraryFailsOrRefusesTheCall() throws Exception {
		// 503: the library is down. 404: a library without the internal endpoints. 401: a wrong token.
		for (int libraryStatus : new int[] { 503, 404, 401 }) {
			LIBRARY.answerStartWith(libraryStatus);

			as("admin", start(entryId))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error").value("service_unavailable"))
					.andExpect(content().string(not(containsString("127.0.0.1"))));
		}
		assertThat(countJobs()).isZero();
		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isFalse();
	}

	// ---- A start that fails or is cancelled while the torrent is added ----

	@Test
	void aTorrentAcceptedBeforeTheClientTimedOutIsRemoved() throws Exception {
		// The client accepts the torrent, then the answer is lost (read timeout).
		doAnswer(call -> {
			call.callRealMethod();
			throw new ServiceUnavailableException("Torrent client unavailable", null);
		}).when(torrents).add(any(), any(), any());

		as("admin", start(entryId))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"));

		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isFalse();
		assertFailedAndLibraryTold("client_error");
	}

	@Test
	void anUnexpectedErrorWhileAddingLeavesNoActiveJob() throws Exception {
		doAnswer(call -> {
			call.callRealMethod();
			throw new IllegalStateException("unexpected");
		}).when(torrents).add(any(), any(), any());

		as("admin", start(entryId))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("internal_error"));

		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isFalse();
		assertFailedAndLibraryTold("client_error");
		// Nothing blocks a new start.
		doAnswer(call -> call.callRealMethod()).when(torrents).add(any(), any(), any());
		as("admin", start(entryId)).andExpect(status().isCreated());
	}

	@Test
	void aCancelThatArrivesWhileTheTorrentIsAddedLeavesNoTorrent() throws Exception {
		// The cancel runs after the job is created and before the torrent is added: it has nothing to
		// remove yet, so the start must remove the torrent it adds.
		doAnswer(call -> {
			UUID jobId = jdbc.queryForObject("select id from download_job where entry_id = ?", UUID.class, entryId);
			downloads.cancel(jobId);
			return call.callRealMethod();
		}).when(torrents).add(any(), any(), any());

		as("admin", start(entryId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.state").value("FAILED"))
				.andExpect(jsonPath("$.errorCode").value("cancelled"));

		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isFalse();
		assertFailedAndLibraryTold("cancelled");
	}

	@Test
	void aLateCleanupOfACancelledStartKeepsTheTorrentOfTheStartThatFollowed() throws Exception {
		// Start A is slow to add its torrent: meanwhile it is cancelled and start B of the same entry,
		// hence of the same info hash, adds the torrent. A's cleanup must leave B's torrent alone.
		UUID b = cancelAndStartAgainWhileTheFirstTorrentIsAdded(call -> call.callRealMethod());

		assertTheSecondStartIsRunning(b);
	}

	@Test
	void aLateFailureOfACancelledStartKeepsTheTorrentOfTheStartThatFollowed() throws Exception {
		UUID b = cancelAndStartAgainWhileTheFirstTorrentIsAdded(call -> {
			throw new ServiceUnavailableException("Torrent client unavailable", null);
		});

		assertTheSecondStartIsRunning(b);
	}

	@Test
	void aLateFailureNoticeOfACancelledAttemptDoesNotUndoTheNextAttempt() throws Exception {
		// A is cancelled. Just before its failure notice is sent, B of the same entry starts. The library must
		// end with B's start: a late /failed would make the entry UNAVAILABLE while B downloads.
		UUID first = startedJob();
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
			// Gives the second start the time to tell the library, if nothing holds it back.
			secondStarted.await(500, TimeUnit.MILLISECONDS);
			return call.callRealMethod();
		}).when(library).downloadFailed(entryId);

		as("admin", post("/downloads/" + first + "/cancel")).andExpect(status().isOk());

		UUID secondId = second.get(5, TimeUnit.SECONDS);
		assertThat(LIBRARY.calls()).extracting(LibraryStub.Call::method, LibraryStub.Call::path).endsWith(
				tuple("POST", "/internal/downloads/" + entryId + "/failed"),
				tuple("PUT", "/internal/downloads/" + entryId));
		assertThat(jdbc.queryForObject("select state from download_job where id = ?", String.class, secondId))
				.isEqualTo("QUEUED");
	}

	// ---- Cancel (consistency rules, "Failing and cancelling") ----

	@Test
	void cancelRemovesTheTorrentAndTellsTheLibrary() throws Exception {
		UUID jobId = startedJob();
		LIBRARY.reset();

		as("admin", post("/downloads/" + jobId + "/cancel"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("FAILED"))
				.andExpect(jsonPath("$.errorCode").value("cancelled"))
				.andExpect(jsonPath("$.finishedAt").isNotEmpty());

		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isFalse();
		assertThat(LIBRARY.calls()).singleElement().satisfies(call -> {
			assertThat(call.method()).isEqualTo("POST");
			assertThat(call.path()).isEqualTo("/internal/downloads/" + entryId + "/failed");
			assertThat(call.token()).isEqualTo(TOKEN);
		});
		assertThat(jdbc.queryForObject("select library_synced_at is not null from download_job where id = ?",
				Boolean.class, jobId)).isTrue();
	}

	@Test
	void aCancelledDownloadIsCancelledEvenWhenTheLibraryCannotBeToldYet() throws Exception {
		UUID jobId = startedJob();
		LIBRARY.answerFailedWith(503);

		as("admin", post("/downloads/" + jobId + "/cancel"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errorCode").value("cancelled"));

		// Still to tell the library: J6 repeats the call at each status check.
		assertThat(jdbc.queryForObject("select library_synced_at is null from download_job where id = ?",
				Boolean.class, jobId)).isTrue();
	}

	@Test
	void aFinishedDownloadCannotBeCancelled() throws Exception {
		UUID jobId = startedJob();
		as("admin", post("/downloads/" + jobId + "/cancel")).andExpect(status().isOk());

		as("admin", post("/downloads/" + jobId + "/cancel"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("download_not_cancellable"))
				.andExpect(jsonPath("$.message").value("This download has already finished and cannot be cancelled."));
	}

	@Test
	void anEntryCanBeDownloadedAgainAfterACancel() throws Exception {
		UUID jobId = startedJob();
		as("admin", post("/downloads/" + jobId + "/cancel")).andExpect(status().isOk());

		as("admin", start(entryId)).andExpect(status().isCreated());
		assertThat(countJobs()).isEqualTo(2);
	}

	@Test
	void anUnknownOrMalformedDownloadIsNotFound() throws Exception {
		as("admin", post("/downloads/" + UUID.randomUUID() + "/cancel")).andExpect(status().isNotFound());
		as("admin", post("/downloads/not-an-id/cancel")).andExpect(status().isNotFound());
		as("admin", get("/downloads/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}

	// ---- Read ----

	@Test
	void aDownloadCanBeRead() throws Exception {
		UUID jobId = startedJob();

		as("admin", get("/downloads/" + jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(jobId.toString()))
				.andExpect(jsonPath("$.state").value("QUEUED"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.finishedAt").value(nullValue()));
	}

	// ---- Helpers ----

	/**
	 * Starts A. While A adds its torrent, A is cancelled and B is started, then A's add ends with
	 * {@code endOfFirstAdd}.
	 *
	 * @return B's id
	 */
	private UUID cancelAndStartAgainWhileTheFirstTorrentIsAdded(Answer<?> endOfFirstAdd) throws Exception {
		AtomicBoolean firstAdd = new AtomicBoolean(true);
		AtomicReference<UUID> second = new AtomicReference<>();
		doAnswer(call -> {
			if (!firstAdd.getAndSet(false)) {
				return call.callRealMethod();
			}
			UUID first = jdbc.queryForObject("select id from download_job where entry_id = ?", UUID.class, entryId);
			downloads.cancel(first);
			second.set(downloads.start(entryId, UUID.randomUUID()).id());
			return endOfFirstAdd.answer(call);
		}).when(torrents).add(any(), any(), any());

		mvc.perform(start(entryId).header("X-Test-Caller", "admin"));
		return second.get();
	}

	private void assertTheSecondStartIsRunning(UUID second) {
		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isTrue();
		assertThat(QbittorrentContainer.torrentInfo(entryHash)).contains("homeflix-job-" + second);
		assertThat(jdbc.queryForList("select state, error_code from download_job where entry_id = ? order by created_at",
				entryId)).extracting(row -> row.get("state"), row -> row.get("error_code")).containsExactly(
						tuple("FAILED", "cancelled"), tuple("QUEUED", null));
	}

	private UUID startedJob() throws Exception {
		return idOf(as("admin", start(entryId)).andExpect(status().isCreated()).andReturn());
	}

	private UUID idOf(MvcResult result) throws Exception {
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asString());
	}

	private static MockHttpServletRequestBuilder start(UUID entry) {
		return post("/downloads").contentType(MediaType.APPLICATION_JSON)
				.content("{\"entryId\":\"" + entry + "\",\"rightsAcknowledged\":true}");
	}

	private ResultActions as(String caller, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("X-Test-Caller", caller));
	}

	/** The only job is FAILED with this reason, and the library was told it started, then that it failed. */
	private void assertFailedAndLibraryTold(String errorCode) {
		Map<String, Object> job = jdbc.queryForMap("select * from download_job where entry_id = ?", entryId);
		assertThat(job.get("state")).isEqualTo("FAILED");
		assertThat(job.get("error_code")).isEqualTo(errorCode);
		assertThat(job.get("library_synced_at")).isNotNull();
		assertThat(LIBRARY.calls()).extracting(LibraryStub.Call::method, LibraryStub.Call::path).containsExactly(
				tuple("PUT", "/internal/downloads/" + entryId),
				tuple("POST", "/internal/downloads/" + entryId + "/failed"));
	}

	private void assertNothingStarted() {
		assertThat(LIBRARY.calls()).isEmpty();
		assertThat(countJobs()).isZero();
		assertThat(QbittorrentContainer.hasTorrent(entryHash)).isFalse();
	}

	private int countJobs() {
		return jdbc.queryForObject("select count(*) from download_job", Integer.class);
	}

	private UUID insertEntry(String title, String hash) {
		return jdbc.queryForObject("""
				insert into acquisition_entry (title, source_name, source_url, licence, licence_url,
				    download_source, expected_size_bytes, expected_format)
				values (?, 'Test source', 'https://example.org/', 'Test licence', 'https://example.org/licence',
				    ?, 1000, 'MP4')
				returning id
				""", UUID.class, title, "magnet:?xt=urn:btih:" + hash + "&dn=test.mp4");
	}

	private static String randomHash() {
		byte[] bytes = new byte[20];
		ThreadLocalRandom.current().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

}
