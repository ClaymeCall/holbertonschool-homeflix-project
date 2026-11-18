package com.homeflix.acquisition.catalogue;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.acquisition.HeaderCallers;
import com.homeflix.acquisition.TestcontainersConfiguration;

/**
 * The verified catalogue endpoints against a real PostgreSQL (ADR-0011): administrator only (ACQ-05),
 * the fields of the contract, and no way to send or read a download address (ACQ-09, ACQ-10, R-15).
 */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class EntryEndpointsTests {

	private static final String SINTEL_MAGNET_HASH = "338806f8ce250642f0525949dcb3be6ccdc9b430";

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
		as("admin", get("/entries"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(sintelId().toString()))
				.andExpect(jsonPath("$[0].title").value("Sintel"))
				.andExpect(jsonPath("$[0].sourceName").value("Blender Foundation"))
				.andExpect(jsonPath("$[0].sourceUrl").value("https://durian.blender.org/"))
				.andExpect(jsonPath("$[0].licence").value("Creative Commons Attribution 3.0 Unported (CC BY 3.0)"))
				.andExpect(jsonPath("$[0].licenceUrl").value("https://creativecommons.org/licenses/by/3.0/"))
				.andExpect(jsonPath("$[0].attribution").value(containsString("© copyright Blender Foundation")))
				.andExpect(jsonPath("$[0].expectedSizeBytes").value(129184608L))
				.andExpect(jsonPath("$[0].expectedFormat").value("MP4"));
	}

	@Test
	void entriesAreSortedByTitleWithoutRegardToCase() throws Exception {
		insertEntry("big Buck Bunny", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
		insertEntry("Tears of Steel", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

		as("admin", get("/entries"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.contains("big Buck Bunny", "Sintel", "Tears of Steel")));
	}

	@Test
	void anEntryWithoutAttributionHasANullAttribution() throws Exception {
		UUID id = insertEntry("Tears of Steel", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

		as("admin", get("/entries/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.attribution").value(org.hamcrest.Matchers.nullValue()));
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
				.andExpect(jsonPath("$.title").value("Sintel"))
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
		String body = "{\"title\":\"Anything\",\"downloadSource\":\"magnet:?xt=urn:btih:" + "c".repeat(40) + "\"}";
		as("admin", post("/entries").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error").value("request_rejected"));
		as("admin", put("/entries/" + sintelId()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isMethodNotAllowed());
		as("admin", delete("/entries/" + sintelId())).andExpect(status().isMethodNotAllowed());

		as("admin", get("/entries")).andExpect(jsonPath("$", hasSize(1)));
	}

	// ---- Helpers ----

	private ResultActions as(String caller, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		return mvc.perform(request.header("X-Test-Caller", caller));
	}

	private UUID sintelId() {
		return jdbc.queryForObject("select id from acquisition_entry where title = 'Sintel'", UUID.class);
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

	/** Leaves only the entries added by the migrations. */
	private void removeTestEntries() {
		jdbc.update("delete from acquisition_entry where title <> 'Sintel'");
	}

}
