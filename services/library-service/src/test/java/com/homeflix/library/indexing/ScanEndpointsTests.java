package com.homeflix.library.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.library.HeaderCallers;
import com.homeflix.library.TestcontainersConfiguration;

/** Manual indexing against a real PostgreSQL and a real temporary folder (LIB-04 to LIB-07, AS-02). */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class ScanEndpointsTests {

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
	@Autowired
	private ScanGate gate;

	private MockMvc mvc;
	private Path films;
	private UUID filmsRoot;

	@BeforeEach
	void setUp() throws IOException {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from media_item");
		jdbc.update("delete from media_root");
		clearMountedFolder();
		films = Files.createDirectory(mounted.resolve("films"));
		filmsRoot = addRoot(films);
	}

	// ---- Permissions (LIB-06, ACC-06) ----

	@Test
	void onlyAnAdministratorCanScan() throws Exception {
		mvc.perform(post("/scans")).andExpect(status().isUnauthorized());
		mvc.perform(post("/scans").header("X-Test-Caller", "user")).andExpect(status().isForbidden());
		assertThat(count("media_item")).isZero();
	}

	// ---- Adding videos (LIB-04, AS-02) ----

	@Test
	void aScanAddsTheVideosOfTheFolderAndItsSubfolders() throws Exception {
		video("The Great Escape.mp4");
		video("Action/Heat (1995).MP4");
		video("Action/Sub Folder/Deep.mp4");

		scan().andExpect(status().isOk())
				.andExpect(jsonPath("$.added").value(3))
				.andExpect(jsonPath("$.updated").value(0))
				.andExpect(jsonPath("$.markedUnavailable").value(0))
				.andExpect(jsonPath("$.unsupported.length()").value(0))
				.andExpect(jsonPath("$.inaccessibleRoots.length()").value(0))
				.andExpect(jsonPath("$.startedAt").exists())
				.andExpect(jsonPath("$.finishedAt").exists());

		assertThat(titles()).containsExactly("Deep", "Heat (1995)", "The Great Escape");
		assertThat(paths()).containsExactly("Action/Heat (1995).MP4", "Action/Sub Folder/Deep.mp4",
				"The Great Escape.mp4");
		assertThat(jdbc.queryForList("select distinct availability from media_item", String.class))
				.containsExactly("AVAILABLE");
	}

	@Test
	void theCatalogueEntryOnlyHasATitle() throws Exception {
		video("Alone.mp4");
		scan().andExpect(status().isOk());
		Map<String, Object> item = jdbc.queryForMap("select * from media_item");
		assertThat(item.get("title")).isEqualTo("Alone");
		assertThat(item.get("description")).isNull();
		assertThat(item.get("poster_url")).isNull();
		assertThat(item.get("duration_seconds")).isNull();
	}

	@Test
	void theSizeOfTheFileIsRecorded() throws Exception {
		Files.writeString(films.resolve("Sized.mp4"), "12345");
		scan().andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select size_bytes from media_file", Long.class)).isEqualTo(5L);
	}

	@Test
	void otherFilesAreIgnoredAndOtherFormatsAreReported() throws Exception {
		video("Good.mp4");
		video("Old.avi");
		video("Series/Episode.MKV");
		Files.writeString(films.resolve("Good.srt"), "subtitles");
		Files.writeString(films.resolve("cover.jpg"), "image");
		Files.writeString(films.resolve(".hidden.mp4"), "hidden");
		Files.writeString(films.resolve("Partial.mp4.part"), "partial");

		scan().andExpect(status().isOk())
				.andExpect(jsonPath("$.added").value(1))
				.andExpect(jsonPath("$.unsupported.length()").value(2))
				.andExpect(jsonPath("$.unsupported[?(@.relativePath == 'Old.avi')].error").value("format_not_supported"))
				.andExpect(jsonPath("$.unsupported[?(@.relativePath == 'Series/Episode.MKV')].rootId")
						.value(filmsRoot.toString()));
		assertThat(titles()).containsExactly("Good");
	}

	@Test
	void anEmptyFolderGivesAnEmptyReport() throws Exception {
		scan().andExpect(status().isOk()).andExpect(jsonPath("$.added").value(0))
				.andExpect(jsonPath("$.updated").value(0));
	}

	@Test
	void aSecondRootWithTheSameRelativePathIsAnotherTitle() throws Exception {
		Path shows = Files.createDirectory(mounted.resolve("shows"));
		addRoot(shows);
		video("Same.mp4");
		Files.writeString(shows.resolve("Same.mp4"), "x");

		scan().andExpect(status().isOk()).andExpect(jsonPath("$.added").value(2));
		assertThat(count("media_item")).isEqualTo(2);
	}

	// ---- Idempotence (LIB-05) ----

	@Test
	void aSecondScanCreatesNothing() throws Exception {
		video("A.mp4");
		video("Dir/B.mp4");
		scan().andExpect(jsonPath("$.added").value(2));
		List<UUID> items = jdbc.queryForList("select id from media_item order by title", UUID.class);

		scan().andExpect(status().isOk())
				.andExpect(jsonPath("$.added").value(0))
				.andExpect(jsonPath("$.updated").value(2))
				.andExpect(jsonPath("$.markedUnavailable").value(0));
		scan().andExpect(jsonPath("$.added").value(0));

		assertThat(count("media_file")).isEqualTo(2);
		assertThat(jdbc.queryForList("select id from media_item order by title", UUID.class)).isEqualTo(items);
	}

	@Test
	void aNewFileIsAddedByALaterScanAndTheOldOnesAreKept() throws Exception {
		video("A.mp4");
		scan();
		video("B.mp4");
		scan().andExpect(jsonPath("$.added").value(1)).andExpect(jsonPath("$.updated").value(1));
		assertThat(titles()).containsExactly("A", "B");
	}

	@Test
	void aScanDoesNotOverwriteWhatAnAdministratorEdited() throws Exception {
		video("Raw name.mp4");
		scan();
		jdbc.update("update media_item set title = 'Nice Title', description = 'About it', duration_seconds = 7200");

		scan();

		Map<String, Object> item = jdbc.queryForMap("select * from media_item");
		assertThat(item.get("title")).isEqualTo("Nice Title");
		assertThat(item.get("description")).isEqualTo("About it");
		assertThat(item.get("duration_seconds")).isEqualTo(7200);
	}

	@Test
	void aChangedSizeIsRecorded() throws Exception {
		Files.writeString(films.resolve("Grows.mp4"), "1");
		scan();
		Files.writeString(films.resolve("Grows.mp4"), "123456");
		scan();
		assertThat(jdbc.queryForObject("select size_bytes from media_file", Long.class)).isEqualTo(6L);
		assertThat(count("media_item")).isEqualTo(1);
	}

	@Test
	void aTitleRemovedByAnAdministratorIsNotCreatedAgain() throws Exception {
		video("Removed.mp4");
		scan();
		jdbc.update("delete from media_item");

		scan().andExpect(jsonPath("$.added").value(0));
		assertThat(count("media_item")).isZero();
	}

	// ---- Files that disappear (LIB-07) ----

	@Test
	void aDeletedFileMakesItsTitleUnavailableAndKeepsItsInformation() throws Exception {
		video("Gone.mp4");
		video("Stays.mp4");
		scan();
		jdbc.update("update media_item set description = 'Keep me' where title = 'Gone'");
		UUID profile = UUID.randomUUID();
		jdbc.update("insert into favourite (profile_id, media_item_id) select ?, id from media_item where title = 'Gone'",
				profile);
		Files.delete(films.resolve("Gone.mp4"));

		scan().andExpect(jsonPath("$.markedUnavailable").value(1)).andExpect(jsonPath("$.updated").value(1));

		assertThat(availability("Gone")).isEqualTo("UNAVAILABLE");
		assertThat(availability("Stays")).isEqualTo("AVAILABLE");
		assertThat(jdbc.queryForObject("select description from media_item where title = 'Gone'", String.class))
				.isEqualTo("Keep me");
		assertThat(count("favourite")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select missing_since is not null from media_file where relative_path = 'Gone.mp4'",
				Boolean.class)).isTrue();
	}

	@Test
	void aMissingFileIsCountedOnceNotOnEveryScan() throws Exception {
		video("Gone.mp4");
		scan();
		Files.delete(films.resolve("Gone.mp4"));
		scan().andExpect(jsonPath("$.markedUnavailable").value(1));
		scan().andExpect(jsonPath("$.markedUnavailable").value(0));
	}

	@Test
	void aFileThatComesBackMakesItsTitleAvailableAgain() throws Exception {
		video("Back.mp4");
		scan();
		Files.delete(films.resolve("Back.mp4"));
		scan();
		assertThat(availability("Back")).isEqualTo("UNAVAILABLE");

		video("Back.mp4");
		scan().andExpect(jsonPath("$.added").value(0)).andExpect(jsonPath("$.updated").value(1));

		assertThat(availability("Back")).isEqualTo("AVAILABLE");
		assertThat(count("media_item")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select missing_since is null from media_file", Boolean.class)).isTrue();
	}

	// ---- Roots that cannot be read (LIB-03) ----

	@Test
	void aRootThatDisappearedIsReportedAndTheOthersAreStillScanned() throws Exception {
		Path shows = Files.createDirectory(mounted.resolve("shows"));
		UUID showsRoot = addRoot(shows);
		video("Film.mp4");
		Files.writeString(shows.resolve("Show.mp4"), "x");
		scan();
		deleteTree(shows);

		scan().andExpect(status().isOk())
				.andExpect(jsonPath("$.inaccessibleRoots.length()").value(1))
				.andExpect(jsonPath("$.inaccessibleRoots[0].rootId").value(showsRoot.toString()))
				.andExpect(jsonPath("$.inaccessibleRoots[0].error").value("directory_not_found"))
				.andExpect(jsonPath("$.updated").value(1))
				.andExpect(jsonPath("$.markedUnavailable").value(1));

		// The folder is gone: its title is unavailable. The other folder is not affected.
		assertThat(availability("Show")).isEqualTo("UNAVAILABLE");
		assertThat(availability("Film")).isEqualTo("AVAILABLE");
	}

	@Test
	void theTitlesOfAFolderThatComesBackAreAvailableAgain() throws Exception {
		Path shows = Files.createDirectory(mounted.resolve("shows"));
		addRoot(shows);
		Files.writeString(shows.resolve("Show.mp4"), "x");
		scan();
		jdbc.update("update media_item set description = 'Keep me' where title = 'Show'");
		UUID profile = UUID.randomUUID();
		jdbc.update("insert into favourite (profile_id, media_item_id) select ?, id from media_item where title = 'Show'", profile);
		deleteTree(shows);
		scan();
		assertThat(availability("Show")).isEqualTo("UNAVAILABLE");
		assertThat(jdbc.queryForObject("select description from media_item where title = 'Show'", String.class))
				.isEqualTo("Keep me");
		assertThat(count("favourite")).isEqualTo(1);

		// The share is mounted again, as it was.
		Files.createDirectory(shows);
		Files.writeString(shows.resolve("Show.mp4"), "x");
		scan().andExpect(jsonPath("$.inaccessibleRoots.length()").value(0))
				.andExpect(jsonPath("$.added").value(0));

		assertThat(availability("Show")).isEqualTo("AVAILABLE");
		assertThat(count("media_item")).isEqualTo(1);
	}

	@Test
	void aRootThatBecameAFileIsReportedAndItsTitlesAreUnavailable() throws Exception {
		Path shows = Files.createDirectory(mounted.resolve("shows"));
		UUID showsRoot = addRoot(shows);
		Files.writeString(shows.resolve("Show.mp4"), "x");
		scan();
		deleteTree(shows);
		Files.writeString(shows, "now a file");

		scan().andExpect(status().isOk())
				.andExpect(jsonPath("$.inaccessibleRoots[0].rootId").value(showsRoot.toString()))
				.andExpect(jsonPath("$.inaccessibleRoots[0].error").value("not_a_directory"));
		assertThat(availability("Show")).isEqualTo("UNAVAILABLE");
	}

	@Test
	void aFolderThatIsEmptyBecauseTheShareIsNotMountedMakesItsTitlesUnavailable() throws Exception {
		video("Film.mp4");
		scan();
		Files.delete(films.resolve("Film.mp4"));

		scan().andExpect(jsonPath("$.markedUnavailable").value(1)).andExpect(jsonPath("$.inaccessibleRoots.length()").value(0));
		assertThat(availability("Film")).isEqualTo("UNAVAILABLE");
	}

	@Test
	void aSubfolderThatCannotBeReadDoesNotMakeItsTitlesUnavailable() throws Exception {
		video("Open/A.mp4");
		video("Locked/B.mp4");
		scan();
		Path locked = films.resolve("Locked");
		Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
		try {
			if (Files.isReadable(locked)) {
				return; // running as a user that can read anything, nothing to test
			}
			scan().andExpect(status().isOk()).andExpect(jsonPath("$.markedUnavailable").value(0));
			assertThat(availability("B")).isEqualTo("AVAILABLE");
		}
		finally {
			Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
		}
	}

	// ---- Links and paths (LIB-08, R-04) ----

	@Test
	void linksAreNotFollowed() throws Exception {
		Path elsewhere = Files.createDirectory(mounted.resolve("elsewhere"));
		Files.writeString(elsewhere.resolve("Outside.mp4"), "x");
		Files.createSymbolicLink(films.resolve("escape"), elsewhere);
		video("Real.mp4");
		Files.createSymbolicLink(films.resolve("Alias.mp4"), films.resolve("Real.mp4"));

		scan().andExpect(jsonPath("$.added").value(1));
		assertThat(titles()).containsExactly("Real");
	}

	@Test
	void namesWithSpacesAndAccentsKeepTheirTitle() throws Exception {
		video("Amélie (2001) - Director's Cut.mp4");
		scan();
		assertThat(titles()).containsExactly("Amélie (2001) - Director's Cut");
	}

	// ---- Storage errors ----

	@Test
	void aDatabaseFailureRollsBackItsFolderAndNeverReachesTheResponse() throws Exception {
		Path shows = Files.createDirectory(mounted.resolve("shows"));
		UUID showsRoot = addRoot(shows);
		video("Good.mp4");
		video("Zebra.mp4");
		Files.writeString(shows.resolve("Show.mp4"), "x");
		dropRefuseZebra();
		jdbc.execute("""
				create function refuse_zebra() returns trigger language plpgsql as
				$$ begin
				  if new.title = 'Zebra' then raise exception 'secret-database-detail'; end if;
				  return new;
				end $$""");
		jdbc.execute("create trigger refuse_zebra before insert on media_item for each row execute function refuse_zebra()");
		try {
			String body = scan().andExpect(status().isOk())
					.andExpect(jsonPath("$.inaccessibleRoots.length()").value(1))
					.andExpect(jsonPath("$.inaccessibleRoots[0].rootId").value(filmsRoot.toString()))
					.andExpect(jsonPath("$.inaccessibleRoots[0].error").value("scan_failed"))
					.andExpect(jsonPath("$.added").value(1))
					.andReturn().getResponse().getContentAsString();
			assertThat(body).doesNotContain("secret-database-detail").doesNotContain("Zebra")
					.doesNotContain(mounted.toString()).doesNotContain("Exception");
		}
		finally {
			dropRefuseZebra();
		}

		// The folder that failed was rolled back as a whole, not half-indexed. The other one is indexed.
		assertThat(paths()).isEmpty();
		assertThat(titles()).containsExactly("Show");
		assertThat(jdbc.queryForObject("select count(*) from media_file where root_id = ?", Integer.class, showsRoot))
				.isEqualTo(1);

		// Once the database accepts the file again, the next scan indexes everything.
		scan().andExpect(status().isOk()).andExpect(jsonPath("$.inaccessibleRoots.length()").value(0))
				.andExpect(jsonPath("$.added").value(2));
		assertThat(titles()).containsExactly("Good", "Show", "Zebra");
	}

	private void dropRefuseZebra() {
		jdbc.execute("drop trigger if exists refuse_zebra on media_item");
		jdbc.execute("drop function if exists refuse_zebra()");
	}

	// ---- One scan at a time ----

	@Test
	void aScanIsRefusedWhileAnotherRuns() throws Exception {
		video("A.mp4");
		assertThat(gate.tryEnter()).isTrue();
		try {
			scan().andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("scan_in_progress"))
					.andExpect(jsonPath("$.message").value("A scan is already running. Try again when it has finished."));
		}
		finally {
			gate.leave();
		}
		assertThat(count("media_item")).isZero();
		scan().andExpect(status().isOk()).andExpect(jsonPath("$.added").value(1));
	}

	@Test
	void theTitleComesFromTheFileNameWithoutItsExtension() {
		assertThat(ScanService.titleOf("Films/The Great Escape.mp4")).isEqualTo("The Great Escape");
		assertThat(ScanService.titleOf("a.b.c.mp4")).isEqualTo("a.b.c");
		assertThat(ScanService.titleOf("  spaced  .mp4")).isEqualTo("spaced");
		assertThat(ScanService.titleOf("x".repeat(600) + ".mp4")).hasSize(500);
	}

	// ---- Helpers ----

	private ResultActions scan() throws Exception {
		return mvc.perform(post("/scans").header("X-Test-Caller", "admin"));
	}

	private void video(String relative) throws IOException {
		Path file = films.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "video");
	}

	private UUID addRoot(Path folder) {
		return jdbc.queryForObject("insert into media_root (path) values (?) returning id", UUID.class,
				folder.toString());
	}

	private List<String> titles() {
		return jdbc.queryForList("select title from media_item order by title", String.class);
	}

	private List<String> paths() {
		return jdbc.queryForList("select relative_path from media_file where root_id = ? order by relative_path",
				String.class, filmsRoot);
	}

	private String availability(String title) {
		return jdbc.queryForObject("select availability from media_item where title = ?", String.class, title);
	}

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	private static void clearMountedFolder() throws IOException {
		try (var children = Files.list(mounted)) {
			for (Path child : children.toList()) {
				if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
					deleteTree(child);
				}
				else {
					Files.delete(child);
				}
			}
		}
	}

	private static void deleteTree(Path directory) throws IOException {
		try (var tree = Files.walk(directory)) {
			tree.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

}
