package com.homeflix.library.roots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.library.TestcontainersConfiguration;
import com.homeflix.library.security.Caller;
import com.homeflix.library.security.CallerProvider;

/** The roots endpoints against a real PostgreSQL: permissions, errors and what removal keeps (LIB-01, LIB-07). */
@Import({ TestcontainersConfiguration.class, RootEndpointsTests.TestCallers.class })
@SpringBootTest
class RootEndpointsTests {

	@TempDir
	static Path mounted;

	@DynamicPropertySource
	static void mountedPaths(DynamicPropertyRegistry registry) {
		registry.add("homeflix.library.mounted-paths", () -> mounted.toString());
	}

	/** Stands in for the token check of issue G5: the header says who is calling. */
	@TestConfiguration(proxyBeanMethods = false)
	static class TestCallers {

		@Bean
		@Primary
		CallerProvider headerCallers() {
			return request -> switch (String.valueOf(request.getHeader("X-Test-Caller"))) {
				case "admin" -> Optional.of(new Caller(UUID.randomUUID(), true, null));
				case "user" -> Optional.of(new Caller(UUID.randomUUID(), false, UUID.randomUUID()));
				default -> Optional.empty();
			};
		}

	}

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;

	@BeforeEach
	void setUp() throws IOException {
		clearMountedFolder();
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from media_item");
		jdbc.update("delete from media_root");
	}

	// ---- Permissions (ACC-06, SEC-02) ----

	@Test
	void anonymousRequestsAreRefused() throws Exception {
		mvc.perform(get("/roots")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
		mvc.perform(post("/roots").contentType(MediaType.APPLICATION_JSON).content(body(mounted)))
				.andExpect(status().isUnauthorized());
		mvc.perform(delete("/roots/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
		assertThat(count("media_root")).isZero();
	}

	@Test
	void anOrdinaryUserIsRefused() throws Exception {
		mvc.perform(get("/roots").header("X-Test-Caller", "user")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
		mvc.perform(post("/roots").header("X-Test-Caller", "user").contentType(MediaType.APPLICATION_JSON)
				.content(body(mounted))).andExpect(status().isForbidden());
		assertThat(count("media_root")).isZero();
	}

	// ---- Add and list ----

	@Test
	void anAdministratorAddsAndListsAFolder() throws Exception {
		Path films = Files.createDirectory(mounted.resolve("films"));
		mvc.perform(add(films)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.path").value(films.toString()))
				.andExpect(jsonPath("$.createdAt").exists());
		mvc.perform(get("/roots").header("X-Test-Caller", "admin")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].path").value(films.toString()));
	}

	@Test
	void aTrailingSlashIsStoredWithoutIt() throws Exception {
		Path films = Files.createDirectory(mounted.resolve("films"));
		mvc.perform(add(films + "/")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.path").value(films.toString()));
	}

	@Test
	void eachProblemHasItsOwnCodeAndMessage() throws Exception {
		Path file = Files.createFile(mounted.resolve("movie.mp4"));
		expectError(add(mounted.resolve("missing")), 400, "directory_not_found", "This folder does not exist.");
		expectError(add(file), 400, "not_a_directory", "This path is a file, not a folder.");
		expectError(add("/etc"), 400, "directory_outside_library",
				"This folder is not inside the folders shared with HomeFlix.");
		assertThat(count("media_root")).isZero();
	}

	@Test
	void aMissingOrRelativePathIsAValidationError() throws Exception {
		mvc.perform(add("")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields.path").exists());
		mvc.perform(add("films")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields.path").exists());
		mvc.perform(post("/roots").header("X-Test-Caller", "admin").contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest());
	}

	@Test
	void technicalDetailsStayOutOfTheResponse() throws Exception {
		String response = mvc.perform(add(mounted.resolve("missing"))).andReturn().getResponse().getContentAsString();
		assertThat(response).doesNotContain(mounted.toString());
	}

	// ---- Duplicates and nesting (LIB-05) ----

	@Test
	void theSameFolderTwiceIsRefused() throws Exception {
		Path films = Files.createDirectory(mounted.resolve("films"));
		mvc.perform(add(films)).andExpect(status().isCreated());
		expectError(add(films), 409, "root_already_added", "This folder is already added.");
		expectError(add(films + "/"), 409, "root_already_added", "This folder is already added.");
		assertThat(count("media_root")).isEqualTo(1);
	}

	@Test
	void aFolderInsideAnExistingRootIsRefused() throws Exception {
		Path films = Files.createDirectory(mounted.resolve("films"));
		Path action = Files.createDirectory(films.resolve("action"));
		mvc.perform(add(films)).andExpect(status().isCreated());
		expectError(add(action), 409, "root_overlaps", "This folder overlaps a folder that is already added.");
		assertThat(count("media_root")).isEqualTo(1);
	}

	@Test
	void aFolderThatContainsAnExistingRootIsRefused() throws Exception {
		Path films = Files.createDirectory(mounted.resolve("films"));
		Path action = Files.createDirectory(films.resolve("action"));
		mvc.perform(add(action)).andExpect(status().isCreated());
		expectError(add(films), 409, "root_overlaps", "This folder overlaps a folder that is already added.");
		assertThat(count("media_root")).isEqualTo(1);
	}

	@Test
	void twoSiblingFoldersAreBothAccepted() throws Exception {
		mvc.perform(add(Files.createDirectory(mounted.resolve("films")))).andExpect(status().isCreated());
		mvc.perform(add(Files.createDirectory(mounted.resolve("films-more")))).andExpect(status().isCreated());
		assertThat(count("media_root")).isEqualTo(2);
	}

	@Test
	void aLinkToAnExistingRootIsTheSameFolder() throws Exception {
		Path films = Files.createDirectory(mounted.resolve("films"));
		Path alias = Files.createSymbolicLink(mounted.resolve("alias"), films);
		mvc.perform(add(films)).andExpect(status().isCreated());
		expectError(add(alias), 409, "root_already_added", "This folder is already added.");
	}

	// ---- Removal (LIB-07, R-03) ----

	@Test
	void removingARootKeepsItsTitlesAsUnavailableAndTheFilesOnDisk() throws Exception {
		Path films = Files.createDirectory(mounted.resolve("films"));
		Path movie = Files.writeString(films.resolve("a.mp4"), "x");
		String id = idOf(mvc.perform(add(films)).andReturn().getResponse().getContentAsString());
		UUID file = jdbc.queryForObject(
				"insert into media_file (root_id, relative_path, size_bytes) values (?, 'a.mp4', 1) returning id",
				UUID.class, UUID.fromString(id));
		UUID item = jdbc.queryForObject(
				"insert into media_item (media_file_id, title, availability) values (?, 'A', 'AVAILABLE') returning id",
				UUID.class, file);
		jdbc.update("insert into favourite (profile_id, media_item_id) values (?, ?)", UUID.randomUUID(), item);

		mvc.perform(delete("/roots/" + id).header("X-Test-Caller", "admin")).andExpect(status().isNoContent());

		assertThat(count("media_root")).isZero();
		assertThat(jdbc.queryForObject("select availability from media_item where id = ?", String.class, item))
				.isEqualTo("UNAVAILABLE");
		assertThat(count("favourite")).isEqualTo(1);
		assertThat(movie).exists();
	}

	@Test
	void removingARootLeavesOtherRootsTitlesAvailable() throws Exception {
		Path films = Files.createDirectory(mounted.resolve("films"));
		Path shows = Files.createDirectory(mounted.resolve("shows"));
		String filmsId = idOf(mvc.perform(add(films)).andReturn().getResponse().getContentAsString());
		String showsId = idOf(mvc.perform(add(shows)).andReturn().getResponse().getContentAsString());
		UUID showFile = jdbc.queryForObject(
				"insert into media_file (root_id, relative_path, size_bytes) values (?, 's.mp4', 1) returning id",
				UUID.class, UUID.fromString(showsId));
		jdbc.update("insert into media_item (media_file_id, title, availability) values (?, 'S', 'AVAILABLE')", showFile);

		mvc.perform(delete("/roots/" + filmsId).header("X-Test-Caller", "admin")).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select availability from media_item", String.class)).isEqualTo("AVAILABLE");
	}

	@Test
	void removingAnUnknownRootIsNotFound() throws Exception {
		mvc.perform(delete("/roots/" + UUID.randomUUID()).header("X-Test-Caller", "admin"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("not_found"));
		mvc.perform(delete("/roots/not-an-id").header("X-Test-Caller", "admin")).andExpect(status().isNotFound());
	}

	// ---- Helpers ----

	/** The mounted folder is shared by the tests, so each starts with it empty. */
	private static void clearMountedFolder() throws IOException {
		try (var children = Files.list(mounted)) {
			for (Path child : children.toList()) {
				if (Files.isDirectory(child, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
					try (var tree = Files.walk(child)) {
						tree.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
					}
				}
				else {
					Files.delete(child);
				}
			}
		}
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder add(Object path) {
		return post("/roots").header("X-Test-Caller", "admin").contentType(MediaType.APPLICATION_JSON)
				.content(body(path));
	}

	private static String body(Object path) {
		return "{\"path\":\"" + path + "\"}";
	}

	private void expectError(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			int status, String code, String message) throws Exception {
		mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.error").value(code))
				.andExpect(jsonPath("$.message").value(message));
	}

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	private static String idOf(String json) {
		return com.jayway.jsonpath.JsonPath.read(json, "$.id");
	}

}
