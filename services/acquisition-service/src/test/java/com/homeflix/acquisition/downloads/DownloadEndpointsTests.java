package com.homeflix.acquisition.downloads;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.acquisition.HeaderCallers;
import com.homeflix.acquisition.TestcontainersConfiguration;
import com.homeflix.acquisition.support.LibraryStub;
import com.homeflix.acquisition.support.QbittorrentContainer;

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
		jdbc.update("delete from acquisition_entry where title <> 'Sintel'");
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

	@Test
	void permissionIsCheckedBeforeTheBody() throws Exception {
		// An ordinary user learns nothing about the rules of the body.
		as("user", post("/downloads").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
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
