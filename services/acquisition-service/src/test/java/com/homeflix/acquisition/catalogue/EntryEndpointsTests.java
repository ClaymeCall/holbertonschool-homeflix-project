package com.homeflix.acquisition.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import com.homeflix.acquisition.HeaderCallers;
import com.homeflix.acquisition.TestcontainersConfiguration;

/**
 * The verified catalogue endpoints against a real PostgreSQL (ADR-0011): administrator only (ACQ-05),
 * the fields of the contract, and no way to send or read a download address (ACQ-09, ACQ-10, R-15).
 */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class EntryEndpointsTests {

	private static final String SINTEL_MAGNET_HASH = "4e626263b96d72a36b1b963f44188bf937004d5f";

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		removeTestEntries();
	}

	@AfterEach
	void cleanUp() {
		removeTestEntries();
	}

	// ---- Permissions (ACQ-05, SEC-02) ----

	@Test
	void anonymousCallerIsRefused() throws Exception {
		mvc.perform(get("/entries"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"))
				.andExpect(jsonPath("$.message").value("Please sign in."));
		mvc.perform(get("/entries/" + sintelId())).andExpect(status().isUnauthorized());
	}

	/** The permission is checked before the id is read, so a malformed id does not answer 404 first. */
	@Test
	void theCallerIsCheckedBeforeTheId() throws Exception {
		mvc.perform(get("/entries/not-an-id"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
		as("user", get("/entries/not-an-id"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
	}

	@Test
	void ordinaryUserIsRefused() throws Exception {
		as("user", get("/entries"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"))
				.andExpect(jsonPath("$.message").value("You do not have permission to do this."));
		as("user", get("/entries/" + sintelId())).andExpect(status().isForbidden());
	}

	// ---- What an administrator sees (ACQ-01, ACQ-02) ----

	@Test
	void administratorSeesTheVerifiedCatalogueWithSourceLicenceAndSize() throws Exception {
		String sintel = "$[?(@.title == 'Sintel')]";
		as("admin", get("/entries"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(sintel + ".id").value(contains(sintelId().toString())))
				.andExpect(jsonPath(sintel + ".sourceName").value(contains("Blender Foundation")))
				.andExpect(jsonPath(sintel + ".sourceUrl").value(contains("https://durian.blender.org/")))
				.andExpect(jsonPath(sintel + ".licence")
						.value(contains("Creative Commons Attribution 3.0 Unported (CC BY 3.0)")))
				.andExpect(jsonPath(sintel + ".licenceUrl").value(contains("https://creativecommons.org/licenses/by/3.0/")))
				.andExpect(jsonPath(sintel + ".attribution").value(contains(containsString("© copyright Blender Foundation"))))
				.andExpect(jsonPath(sintel + ".expectedSizeBytes").value(contains(129184608)))
				.andExpect(jsonPath(sintel + ".expectedFormat").value(contains("MP4")));
	}

	/** Only the relative order of known titles is checked, so a new migrated entry does not break it. */
	@Test
	void entriesAreSortedByTitleWithoutRegardToCase() throws Exception {
		insertEntry("aaa test entry", "a".repeat(40));
		insertEntry("ZZZ test entry", "b".repeat(40));

		String body = as("admin", get("/entries")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> titles = JsonPath.read(body, "$[*].title");

		assertThat(titles).contains("aaa test entry", "Sintel", "ZZZ test entry");
		assertThat(titles.indexOf("aaa test entry")).isLessThan(titles.indexOf("Sintel"));
		assertThat(titles.indexOf("Sintel")).isLessThan(titles.indexOf("ZZZ test entry"));
	}

	@Test
	void anEntryWithoutAttributionHasANullAttribution() throws Exception {
		UUID id = insertEntry("ZZZ test entry", "b".repeat(40));

		as("admin", get("/entries/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.attribution").value(nullValue()));
	}

	@Test
	void theDownloadAddressIsNeverReturned() throws Exception {
		for (String path : new String[] { "/entries", "/entries/" + sintelId() }) {
			as("admin", get(path))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$..downloadSource").isEmpty())
					.andExpect(content().string(not(containsString("magnet:"))))
					.andExpect(content().string(not(containsString(SINTEL_MAGNET_HASH))));
		}
	}

	@Test
	void oneEntryIsReturnedById() throws Exception {
		as("admin", get("/entries/" + sintelId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(sintelId().toString()))
				.andExpect(jsonPath("$.title").value("Sintel"))
				.andExpect(jsonPath("$.licence").value("Creative Commons Attribution 3.0 Unported (CC BY 3.0)"))
				.andExpect(jsonPath("$.expectedSizeBytes").value(129184608L));
	}

	@Test
	void unknownOrMalformedIdIsNotFound() throws Exception {
		as("admin", get("/entries/" + UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));
		as("admin", get("/entries/not-an-id"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"))
				.andExpect(content().string(not(containsString("UUID"))));
	}

	// ---- No arbitrary source (ACQ-09, ACQ-10, R-15) ----

	@Test
	void noOperationAcceptsAnEntryOrAUrl() throws Exception {
		int before = entryCount();
		String body = "{\"title\":\"Anything\",\"downloadSource\":\"magnet:?xt=urn:btih:" + "c".repeat(40) + "\"}";
		as("admin", post("/entries").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error").value("request_rejected"));
		as("admin", put("/entries/" + sintelId()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isMethodNotAllowed());
		as("admin", delete("/entries/" + sintelId())).andExpect(status().isMethodNotAllowed());

		as("admin", get("/entries")).andExpect(jsonPath("$[*].title").value(not(hasItem("Anything"))));
		assertThat(entryCount()).isEqualTo(before);
	}

	// ---- Helpers ----

	private ResultActions as(String caller, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("X-Test-Caller", caller));
	}

	private UUID sintelId() {
		return jdbc.queryForObject("select id from acquisition_entry where title = 'Sintel'", UUID.class);
	}

	private int entryCount() {
		return jdbc.queryForObject("select count(*) from acquisition_entry", Integer.class);
	}

	private UUID insertEntry(String title, String infoHash) {
		return jdbc.queryForObject("""
				insert into acquisition_entry (title, source_name, source_url, licence, licence_url,
				    download_source, expected_size_bytes, expected_format)
				values (?, 'Test source', 'https://example.org/', 'Test licence', 'https://example.org/licence',
				    ?, 1000, 'MP4')
				returning id
				""", UUID.class, title, "magnet:?xt=urn:btih:" + infoHash);
	}

	/** Removes the entries this class inserted, and only them: the migrated entries are kept. */
	private void removeTestEntries() {
		jdbc.update("delete from acquisition_entry where source_name = 'Test source'");
	}

}
