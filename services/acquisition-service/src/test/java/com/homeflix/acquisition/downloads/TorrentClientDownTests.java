package com.homeflix.acquisition.downloads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.acquisition.HeaderCallers;
import com.homeflix.acquisition.TestcontainersConfiguration;
import com.homeflix.acquisition.support.LibraryStub;

/**
 * A start while the torrent client is down (its URL is the test default, where nothing listens): the
 * administrator gets the common 503, the job is FAILED with client_error, and the library, told first,
 * is told the download failed (consistency rules, "Starting", step 4).
 */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class TorrentClientDownTests {

	private static final LibraryStub LIBRARY = new LibraryStub();

	@DynamicPropertySource
	static void library(DynamicPropertyRegistry registry) {
		registry.add("homeflix.library.base-url", LIBRARY::url);
	}

	@AfterAll
	static void stopLibrary() {
		LIBRARY.stop();
	}

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void clean() {
		jdbc.update("delete from download_job");
		jdbc.update("delete from acquisition_entry where source_name = 'Test source'");
	}

	@Test
	void aStartWhileTheTorrentClientIsDownFailsVisiblyAndTellsTheLibrary() throws Exception {
		UUID entryId = jdbc.queryForObject("""
				insert into acquisition_entry (title, source_name, source_url, licence, licence_url,
				    download_source, expected_size_bytes, expected_format)
				values ('Test Film', 'Test source', 'https://example.org/', 'Test licence', 'https://example.org/licence',
				    'magnet:?xt=urn:btih:dddddddddddddddddddddddddddddddddddddddd', 1000, 'MP4')
				returning id
				""", UUID.class);
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

		mvc.perform(post("/downloads").header("X-Test-Caller", "admin").contentType(MediaType.APPLICATION_JSON)
						.content("{\"entryId\":\"" + entryId + "\",\"rightsAcknowledged\":true}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("service_unavailable"))
				.andExpect(content().string(not(containsString("127.0.0.1"))))
				.andExpect(content().string(not(containsString("Exception"))));

		Map<String, Object> job = jdbc.queryForMap("select * from download_job where entry_id = ?", entryId);
		assertThat(job.get("state")).isEqualTo("FAILED");
		assertThat(job.get("error_code")).isEqualTo("client_error");
		assertThat(job.get("client_hash")).isEqualTo("d".repeat(40));
		assertThat(job.get("finished_at")).isNotNull();
		assertThat(job.get("library_synced_at")).isNotNull();
		assertThat(LIBRARY.calls()).extracting(LibraryStub.Call::method, LibraryStub.Call::path).containsExactly(
				tuple("PUT", "/internal/downloads/" + entryId),
				tuple("POST", "/internal/downloads/" + entryId + "/failed"));
	}

}
