package com.homeflix.library.downloads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.library.HeaderCallers;
import com.homeflix.library.TestcontainersConfiguration;

/**
 * The calls of the acquisition service (J8, CAT-01, ACQ-07, LIB-05, LIB-08) against a real PostgreSQL and a real
 * temporary downloads folder.
 */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class DownloadEndpointsTests {

	private static final String TOKEN = "test-acquisition-token-not-a-secret";

	@TempDir
	static Path mounted;

	@DynamicPropertySource
	static void paths(DynamicPropertyRegistry registry) {
		registry.add("homeflix.library.mounted-paths", () -> mounted.toString());
		registry.add("homeflix.library.downloads-path", () -> mounted.resolve("downloads").toString());
	}

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private Path downloads;
	private UUID downloadsRoot;
	private UUID entry;

	@BeforeEach
	void setUp() throws IOException {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from media_item");
		jdbc.update("delete from media_root");
		clearMountedFolder();
		downloads = Files.createDirectory(mounted.resolve("downloads"));
		downloadsRoot = addRoot(downloads);
		entry = UUID.randomUUID();
	}

	// ---- Who may call (ADR-0022) ----

	@Test
	void onlyTheAcquisitionServiceCanCall() throws Exception {
		mvc.perform(put("/internal/downloads/{id}", entry).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Sintel\"}")).andExpect(status().isUnauthorized());
		mvc.perform(starting(entry, "Sintel").header("X-Service-Token", "wrong")).andExpect(status().isUnauthorized());
		mvc.perform(post("/internal/downloads/{id}/failed", entry)).andExpect(status().isUnauthorized());
		// A signed-in administrator is not a service.
		mvc.perform(starting(entry, "Sintel").header("X-Service-Token", "").header("X-Test-Caller", "admin"))
				.andExpect(status().isUnauthorized());
		assertThat(count("media_item")).isZero();
	}

	// ---- Starting (CAT-01) ----

	@Test
	void aDownloadStartsAsADownloadingTitle() throws Exception {
		call(starting(entry, "  Sintel  ")).andExpect(status().isOk());

		assertThat(jdbc.queryForMap("select title, availability, media_file_id from media_item where acquisition_entry_id = ?",
				entry)).containsEntry("title", "Sintel").containsEntry("availability", "DOWNLOADING")
				.containsEntry("media_file_id", null);
	}

	@Test
	void startingTwiceCreatesNoSecondEntry() throws Exception {
		call(starting(entry, "Sintel")).andExpect(status().isOk());
		call(starting(entry, "Sintel")).andExpect(status().isOk());

		assertThat(count("media_item")).isEqualTo(1);
	}

	@Test
	void aFailedDownloadStartsAgainAndKeepsWhatWasEdited() throws Exception {
		call(starting(entry, "Sintel")).andExpect(status().isOk());
		jdbc.update("update media_item set description = 'Edited by an administrator', availability = 'UNAVAILABLE'");

		call(starting(entry, "Sintel (new title)")).andExpect(status().isOk());

		assertThat(jdbc.queryForMap("select title, description, availability from media_item"))
				.containsEntry("title", "Sintel").containsEntry("description", "Edited by an administrator")
				.containsEntry("availability", "DOWNLOADING");
	}

	@Test
	void aTitleAlreadyInTheLibraryIsRefused() throws Exception {
		video("Sintel.mp4");
		call(starting(entry, "Sintel")).andExpect(status().isOk());
		call(completed(entry, "Sintel.mp4")).andExpect(status().isOk());

		call(starting(entry, "Sintel")).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("already_in_library"));
		assertThat(availability()).isEqualTo("AVAILABLE");
	}

	@Test
	void aBlankOrTooLongTitleIsRefused() throws Exception {
		call(starting(entry, "   ")).andExpect(status().isBadRequest());
		call(starting(entry, "x".repeat(501))).andExpect(status().isBadRequest());
		assertThat(count("media_item")).isZero();
	}

	// ---- Completing (ACQ-07, LIB-05) ----

	@Test
	void aCompletedFileBecomesAvailableAndPlayable() throws Exception {
		video("sintel/Sintel.mp4");
		call(starting(entry, "Sintel")).andExpect(status().isOk());

		MvcResult result = call(completed(entry, "sintel/Sintel.mp4")).andExpect(status().isOk())
				.andExpect(jsonPath("$.mediaId").exists()).andReturn();

		assertThat(jdbc.queryForMap("select availability, media_file_id from media_item where acquisition_entry_id = ?", entry))
				.containsEntry("availability", "AVAILABLE");
		assertThat(jdbc.queryForObject("select relative_path from media_file where root_id = ?", String.class, downloadsRoot))
				.isEqualTo("sintel/Sintel.mp4");
		UUID mediaId = jdbc.queryForObject("select id from media_item where acquisition_entry_id = ?", UUID.class, entry);
		assertThat(result.getResponse().getContentAsString()).contains(mediaId.toString());
	}

	@Test
	void completingTwiceChangesNothing() throws Exception {
		video("Sintel.mp4");
		call(starting(entry, "Sintel")).andExpect(status().isOk());
		call(completed(entry, "Sintel.mp4")).andExpect(status().isOk());
		call(completed(entry, "Sintel.mp4")).andExpect(status().isOk());

		assertThat(count("media_item")).isEqualTo(1);
		assertThat(count("media_file")).isEqualTo(1);
	}

	@Test
	void aScanThatFoundTheFileFirstLeavesNoDuplicate() throws Exception {
		video("Sintel.mp4");
		// The scan indexed the file and made its own entry before the handoff arrived.
		UUID file = jdbc.queryForObject("insert into media_file (root_id, relative_path, size_bytes) values (?, 'Sintel.mp4', 5) returning id",
				UUID.class, downloadsRoot);
		jdbc.update("insert into media_item (media_file_id, title, availability) values (?, 'Sintel', 'AVAILABLE')", file);
		call(starting(entry, "Sintel (download)")).andExpect(status().isOk());

		call(completed(entry, "Sintel.mp4")).andExpect(status().isOk());

		assertThat(count("media_item")).isEqualTo(1);
		assertThat(count("media_file")).isEqualTo(1);
		assertThat(jdbc.queryForMap("select title, availability, acquisition_entry_id from media_item"))
				.containsEntry("title", "Sintel (download)").containsEntry("availability", "AVAILABLE")
				.containsEntry("acquisition_entry_id", entry);
	}

	@Test
	void aLaterScanAddsNothingForADownloadedFile() throws Exception {
		video("Sintel.mp4");
		call(starting(entry, "Sintel")).andExpect(status().isOk());
		call(completed(entry, "Sintel.mp4")).andExpect(status().isOk());

		mvc.perform(post("/scans").header("X-Test-Caller", "admin")).andExpect(status().isOk());

		assertThat(count("media_item")).isEqualTo(1);
		assertThat(count("media_file")).isEqualTo(1);
		assertThat(availability()).isEqualTo("AVAILABLE");
	}

	@Test
	void anUnknownDownloadIsNotFound() throws Exception {
		video("Sintel.mp4");
		call(completed(entry, "Sintel.mp4")).andExpect(status().isNotFound());
		assertThat(count("media_file")).isZero();
	}

	@Test
	void aCancelledDownloadIsNotBroughtBackByALateCompletion() throws Exception {
		video("Sintel.mp4");
		call(starting(entry, "Sintel")).andExpect(status().isOk());
		call(failed(entry)).andExpect(status().isNoContent());

		call(completed(entry, "Sintel.mp4")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("not_downloading"));
		assertThat(availability()).isEqualTo("UNAVAILABLE");
		assertThat(count("media_file")).isZero();
	}

	@Test
	void aSecondFileForAnAvailableTitleIsRefused() throws Exception {
		video("Sintel.mp4");
		video("Other.mp4");
		call(starting(entry, "Sintel")).andExpect(status().isOk());
		call(completed(entry, "Sintel.mp4")).andExpect(status().isOk());

		call(completed(entry, "Other.mp4")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("already_in_library"));
	}

	// ---- Paths (LIB-08, R-01) ----

	@Test
	void aPathThatLeavesTheDownloadsFolderIsRefused() throws Exception {
		Files.writeString(mounted.resolve("secret.mp4"), "outside");
		call(starting(entry, "Sintel")).andExpect(status().isOk());

		for (String path : new String[] { "../secret.mp4", "a/../../secret.mp4", "/etc/passwd", "a\\b.mp4", "./Sintel.mp4",
				"a//b.mp4", "a/..", "nul\0.mp4" }) {
			call(completed(entry, path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_path"));
		}
		assertThat(availability()).isEqualTo("DOWNLOADING");
	}

	@Test
	void aLinkLeadingOutsideTheDownloadsFolderIsRefused() throws Exception {
		Path outside = Files.writeString(mounted.resolve("secret.mp4"), "outside");
		Files.createSymbolicLink(downloads.resolve("link.mp4"), outside);
		Path elsewhere = Files.createDirectory(mounted.resolve("elsewhere"));
		Files.writeString(elsewhere.resolve("inside.mp4"), "outside");
		Files.createSymbolicLink(downloads.resolve("folder"), elsewhere);
		call(starting(entry, "Sintel")).andExpect(status().isOk());

		call(completed(entry, "link.mp4")).andExpect(status().isBadRequest());
		call(completed(entry, "folder/inside.mp4")).andExpect(status().isBadRequest());
		assertThat(availability()).isEqualTo("DOWNLOADING");
		assertThat(count("media_file")).isZero();
	}

	@Test
	void aMissingFolderOrUnsupportedFileIsRefused() throws Exception {
		Files.writeString(downloads.resolve("notes.txt"), "text");
		Files.writeString(downloads.resolve("Film.mkv"), "mkv");
		Files.createDirectory(downloads.resolve("Folder.mp4"));
		call(starting(entry, "Sintel")).andExpect(status().isOk());

		for (String path : new String[] { "missing.mp4", "notes.txt", "Film.mkv", "Folder.mp4" }) {
			call(completed(entry, path)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("file_not_available"));
		}
		assertThat(availability()).isEqualTo("DOWNLOADING");
	}

	@Test
	void theDownloadsFolderMustBeAnAuthorizedRoot() throws Exception {
		jdbc.update("delete from media_root");
		video("Sintel.mp4");
		call(starting(entry, "Sintel")).andExpect(status().isOk());

		call(completed(entry, "Sintel.mp4")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("downloads_folder_not_authorized"));
		assertThat(availability()).isEqualTo("DOWNLOADING");
	}

	// ---- Failing (CAT-01) ----

	@Test
	void aFailedDownloadBecomesUnavailableAndKeepsItsTitle() throws Exception {
		call(starting(entry, "Sintel")).andExpect(status().isOk());

		call(failed(entry)).andExpect(status().isNoContent());

		assertThat(jdbc.queryForMap("select title, availability from media_item"))
				.containsEntry("title", "Sintel").containsEntry("availability", "UNAVAILABLE");
	}

	@Test
	void aFailureNeverTouchesAnAvailableOrUnknownEntry() throws Exception {
		video("Sintel.mp4");
		call(starting(entry, "Sintel")).andExpect(status().isOk());
		call(completed(entry, "Sintel.mp4")).andExpect(status().isOk());

		// The library may have been told "completed" and the reply lost: a later "failed" must not undo it.
		call(failed(entry)).andExpect(status().isNoContent());
		call(failed(UUID.randomUUID())).andExpect(status().isNoContent());

		assertThat(availability()).isEqualTo("AVAILABLE");
	}

	// ---- Helpers ----

	private org.springframework.test.web.servlet.ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("X-Service-Token", TOKEN));
	}

	private MockHttpServletRequestBuilder starting(UUID id, String title) {
		return put("/internal/downloads/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + title + "\"}");
	}

	private MockHttpServletRequestBuilder completed(UUID id, String relativePath) {
		return post("/internal/downloads/{id}/completed", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"relativePath\":\"" + relativePath.replace("\\", "\\\\").replace("\0", "\\u0000") + "\"}");
	}

	private MockHttpServletRequestBuilder failed(UUID id) {
		return post("/internal/downloads/{id}/failed", id);
	}

	private void video(String relative) throws IOException {
		Path file = downloads.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "video");
	}

	private UUID addRoot(Path folder) {
		return jdbc.queryForObject("insert into media_root (path) values (?) returning id", UUID.class, folder.toString());
	}

	private String availability() {
		return jdbc.queryForObject("select availability from media_item where acquisition_entry_id = ?", String.class, entry);
	}

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	private static void clearMountedFolder() throws IOException {
		try (var children = Files.list(mounted)) {
			for (Path child : children.toList()) {
				if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
					try (var tree = Files.walk(child)) {
						tree.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
					}
				}
				else {
					Files.delete(child);
				}
			}
		}
	}

}
