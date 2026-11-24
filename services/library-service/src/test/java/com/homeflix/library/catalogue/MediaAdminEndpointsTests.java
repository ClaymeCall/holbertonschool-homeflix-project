package com.homeflix.library.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.library.HeaderCallers;
import com.homeflix.library.TestcontainersConfiguration;

/** Metadata editing and removal of catalogue entries by an administrator (CAT-09, CAT-10, LIB-12, R-03). */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class MediaAdminEndpointsTests {

	@TempDir
	static Path mounted;

	@DynamicPropertySource
	static void mountedPaths(DynamicPropertyRegistry registry) {
		registry.add("homeflix.library.mounted-paths", () -> mounted.toString());
	}

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private Path films;

	@BeforeEach
	void setUp() throws IOException {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from media_item");
		jdbc.update("delete from genre");
		jdbc.update("delete from media_file");
		jdbc.update("delete from media_root");
		try (var children = Files.list(mounted)) {
			for (Path child : children.toList()) {
				try (var tree = Files.walk(child)) {
					for (Path path : tree.sorted(java.util.Comparator.reverseOrder()).toList()) {
						Files.delete(path);
					}
				}
			}
		}
		films = Files.createDirectory(mounted.resolve("films"));
		jdbc.update("insert into media_root (path) values (?)", films.toString());
	}

	// ---- Permissions (ACC-06) ----

	@Test
	void anonymousAndOrdinaryUsersCannotEditOrRemove() throws Exception {
		UUID id = item("Heat");
		String path = "/media/" + id;

		mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body("Changed")))
				.andExpect(status().isUnauthorized());
		mvc.perform(delete(path)).andExpect(status().isUnauthorized());
		mvc.perform(put(path).header("X-Test-Caller", "user").contentType(MediaType.APPLICATION_JSON)
				.content(body("Changed"))).andExpect(status().isForbidden());
		mvc.perform(delete(path).header("X-Test-Caller", "user")).andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("select title from media_item where id = ?", String.class, id)).isEqualTo("Heat");
	}

	// ---- Editing (CAT-09, CAT-10) ----

	@Test
	void anAdministratorEditsEveryField() throws Exception {
		UUID id = item("heat.1995.1080p");

		edit(id, """
				{"title": "  Heat  ", "description": "A thief and a cop.", "genres": ["Crime", "Drama"],
				 "durationSeconds": 10200, "posterUrl": "https://upload.wikimedia.org/heat.jpg"}
				""").andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.title").value("Heat"))
				.andExpect(jsonPath("$.description").value("A thief and a cop."))
				.andExpect(jsonPath("$.genres[0]").value("Crime"))
				.andExpect(jsonPath("$.genres[1]").value("Drama"))
				.andExpect(jsonPath("$.durationSeconds").value(10200))
				.andExpect(jsonPath("$.posterUrl").value("https://upload.wikimedia.org/heat.jpg"))
				.andExpect(jsonPath("$.availability").value("AVAILABLE"));

		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/media/" + id)
				.header("X-Test-Caller", "user")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Heat"));
	}

	@Test
	void editingReplacesTheGenresAndReusesAnExistingOneWhateverItsCase() throws Exception {
		UUID id = item("Heat");
		edit(id, body("Heat", "[\"Drama\", \"Crime\"]")).andExpect(status().isOk());
		edit(id, body("Heat", "[\"drama\", \"Thriller\", \"DRAMA\"]")).andExpect(status().isOk())
				.andExpect(jsonPath("$.genres.length()").value(2))
				.andExpect(jsonPath("$.genres[0]").value("Drama"))
				.andExpect(jsonPath("$.genres[1]").value("Thriller"));

		assertThat(jdbc.queryForList("select name from genre order by name", String.class))
				.containsExactly("Crime", "Drama", "Thriller");
	}

	@Test
	void optionalFieldsAreClearedWhenLeftOut() throws Exception {
		UUID id = item("Heat");
		edit(id, body("Heat", "[\"Drama\"]")).andExpect(status().isOk());
		edit(id, "{\"title\": \"Heat\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.description").doesNotExist())
				.andExpect(jsonPath("$.posterUrl").doesNotExist())
				.andExpect(jsonPath("$.durationSeconds").doesNotExist())
				.andExpect(jsonPath("$.genres.length()").value(0));
	}

	@Test
	void editingDoesNotChangeAvailabilityOrTheFile() throws Exception {
		UUID id = item("Heat");
		UUID fileBefore = jdbc.queryForObject("select media_file_id from media_item where id = ?", UUID.class, id);

		edit(id, body("Renamed")).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select media_file_id from media_item where id = ?", UUID.class, id))
				.isEqualTo(fileBefore);
		assertThat(jdbc.queryForObject("select availability from media_item where id = ?", String.class, id))
				.isEqualTo("AVAILABLE");
	}

	@Test
	void badValuesAreRefusedWithTheFieldNamedAndNothingIsSaved() throws Exception {
		UUID id = item("Heat");
		refused(id, "{\"title\": \"  \"}", "title");
		refused(id, "{}", "title");
		refused(id, "{\"title\": \"" + "x".repeat(501) + "\"}", "title");
		refused(id, "{\"title\": \"T\", \"description\": \"" + "x".repeat(5001) + "\"}", "description");
		refused(id, "{\"title\": \"T\", \"durationSeconds\": -1}", "durationSeconds");
		refused(id, "{\"title\": \"T\", \"posterUrl\": \"http://upload.wikimedia.org/a.jpg\"}", "posterUrl");
		refused(id, "{\"title\": \"T\", \"posterUrl\": \"javascript:alert(1)\"}", "posterUrl");
		refused(id, "{\"title\": \"T\", \"posterUrl\": \"https://evil.example.com/a.jpg\"}", "posterUrl");
		refused(id, "{\"title\": \"T\", \"posterUrl\": \"https://user:pw@upload.wikimedia.org/a.jpg\"}", "posterUrl");
		refused(id, "{\"title\": \"T\", \"posterUrl\": \"https://exa mple.org/a.jpg\"}", "posterUrl");
		refused(id, "{\"title\": \"T\", \"genres\": [\"  \"]}", "genres");
		refused(id, "{\"title\": \"T\", \"genres\": [\"" + "g".repeat(51) + "\"]}", "genres");
		refused(id, "{\"title\": \"T\", \"genres\": [\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\",\"11\"]}",
				"genres");

		assertThat(jdbc.queryForObject("select title from media_item where id = ?", String.class, id)).isEqualTo("Heat");
		assertThat(jdbc.queryForObject("select count(*) from genre", Integer.class)).isZero();
	}

	@Test
	void editingAnUnknownTitleIsNotFound() throws Exception {
		edit(UUID.randomUUID(), body("X")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));
		mvc.perform(put("/media/not-an-id").header("X-Test-Caller", "admin").contentType(MediaType.APPLICATION_JSON)
				.content(body("X"))).andExpect(status().isNotFound());
	}

	@Test
	void anAdministratorCanCompleteATitleThatOnlyHasWhatTheFileNameGave() throws Exception {
		Files.writeString(films.resolve("Some.Film.2020.mp4"), "video");
		mvc.perform(post("/scans").header("X-Test-Caller", "admin")).andExpect(status().isOk());
		UUID id = jdbc.queryForObject("select id from media_item", UUID.class);

		edit(id, "{\"title\": \"Some Film\", \"description\": \"Now described.\"}").andExpect(status().isOk());
		// A later scan must leave the edit alone (data model, "two tables for one title").
		mvc.perform(post("/scans").header("X-Test-Caller", "admin")).andExpect(status().isOk())
				.andExpect(jsonPath("$.added").value(0));

		assertThat(jdbc.queryForObject("select title from media_item", String.class)).isEqualTo("Some Film");
		assertThat(jdbc.queryForObject("select description from media_item", String.class)).isEqualTo("Now described.");
	}

	// ---- Removal (LIB-12, R-03) ----

	@Test
	void removingAnEntryDeletesTheRowButNeverTheFile() throws Exception {
		Path movie = Files.writeString(films.resolve("Heat.mp4"), "video");
		UUID id = item("Heat", "Heat.mp4");
		UUID profile = UUID.randomUUID();
		jdbc.update("insert into favourite (profile_id, media_item_id) values (?, ?)", profile, id);

		mvc.perform(delete("/media/" + id).header("X-Test-Caller", "admin")).andExpect(status().isNoContent());

		assertThat(movie).exists().hasContent("video");
		assertThat(jdbc.queryForObject("select count(*) from media_item", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from favourite", Integer.class)).isZero();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/media/" + id)
				.header("X-Test-Caller", "user")).andExpect(status().isNotFound());
	}

	@Test
	void aRemovedEntryDoesNotReappearOnTheNextScan() throws Exception {
		Path movie = Files.writeString(films.resolve("Heat.mp4"), "video");
		mvc.perform(post("/scans").header("X-Test-Caller", "admin")).andExpect(jsonPath("$.added").value(1));
		UUID id = jdbc.queryForObject("select id from media_item", UUID.class);

		mvc.perform(delete("/media/" + id).header("X-Test-Caller", "admin")).andExpect(status().isNoContent());
		mvc.perform(post("/scans").header("X-Test-Caller", "admin")).andExpect(status().isOk())
				.andExpect(jsonPath("$.added").value(0));

		assertThat(jdbc.queryForObject("select count(*) from media_item", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from media_file", Integer.class)).isEqualTo(1);
		assertThat(movie).exists();
	}

	@Test
	void removingAnUnknownEntryIsNotFound() throws Exception {
		mvc.perform(delete("/media/" + UUID.randomUUID()).header("X-Test-Caller", "admin"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("not_found"));
		mvc.perform(delete("/media/not-an-id").header("X-Test-Caller", "admin")).andExpect(status().isNotFound());
	}

	// ---- Helpers ----

	private ResultActions edit(UUID id, String json) throws Exception {
		return mvc.perform(put("/media/" + id).header("X-Test-Caller", "admin").contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private void refused(UUID id, String json, String field) throws Exception {
		edit(id, json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields." + field).exists());
	}

	private static String body(String title) {
		return "{\"title\": \"" + title + "\"}";
	}

	private static String body(String title, String genresJson) {
		return "{\"title\": \"" + title + "\", \"genres\": " + genresJson + "}";
	}

	/** An available title backed by a file row. */
	private UUID item(String title) {
		return item(title, title + ".mp4");
	}

	private UUID item(String title, String relativePath) {
		UUID root = jdbc.queryForObject("select id from media_root", UUID.class);
		UUID file = jdbc.queryForObject(
				"insert into media_file (root_id, relative_path, size_bytes) values (?, ?, 5) returning id", UUID.class,
				root, relativePath);
		return jdbc.queryForObject(
				"insert into media_item (media_file_id, title, availability) values (?, ?, 'AVAILABLE') returning id",
				UUID.class, file, title);
	}

}
