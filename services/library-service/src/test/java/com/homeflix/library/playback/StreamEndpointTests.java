package com.homeflix.library.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.library.HeaderCallers;
import com.homeflix.library.TestcontainersConfiguration;

/** Streaming with HTTP Range against a real PostgreSQL and real files (PLY-03, PLY-04, PLY-05, TST-04). */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class StreamEndpointTests {

	/** 1000 bytes where the byte at position i is the letter 'a' plus i modulo 26, so any slice can be checked. */
	private static final byte[] CONTENT = new byte[1000];

	static {
		for (int i = 0; i < CONTENT.length; i++) {
			CONTENT[i] = (byte) ('a' + i % 26);
		}
	}

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
	private UUID root;
	private UUID title;

	@BeforeEach
	void setUp() throws IOException {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from media_item");
		jdbc.update("delete from media_root");
		clearMountedFolder();
		films = Files.createDirectory(mounted.resolve("films"));
		root = jdbc.queryForObject("insert into media_root (path) values (?) returning id", UUID.class, films.toString());
		Files.write(films.resolve("movie.mp4"), CONTENT);
		title = indexed("movie.mp4");
	}

	// ---- The whole file ----

	@Test
	void withoutARangeTheWholeFileIsSent() throws Exception {
		MvcResult result = stream(title).andExpect(status().isOk())
				.andExpect(header().string("Accept-Ranges", "bytes"))
				.andExpect(header().longValue("Content-Length", 1000))
				.andExpect(header().string("Content-Type", "video/mp4"))
				.andExpect(header().doesNotExist("Content-Range")).andReturn();
		assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(CONTENT);
	}

	// ---- Partial content (PLY-03) ----

	@Test
	void aClosedRangeGivesAPartialAnswerWithExactlyThoseBytes() throws Exception {
		MvcResult result = stream(title, "bytes=100-199").andExpect(status().isPartialContent())
				.andExpect(header().string("Content-Range", "bytes 100-199/1000"))
				.andExpect(header().longValue("Content-Length", 100))
				.andExpect(header().string("Accept-Ranges", "bytes"))
				.andExpect(header().string("Content-Type", "video/mp4")).andReturn();
		assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(slice(100, 199));
	}

	@Test
	void anOpenEndedRangeGoesToTheEndOfTheFile() throws Exception {
		MvcResult result = stream(title, "bytes=900-").andExpect(status().isPartialContent())
				.andExpect(header().string("Content-Range", "bytes 900-999/1000"))
				.andExpect(header().longValue("Content-Length", 100)).andReturn();
		assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(slice(900, 999));
	}

	@Test
	void aSuffixRangeGivesTheLastBytes() throws Exception {
		MvcResult result = stream(title, "bytes=-10").andExpect(status().isPartialContent())
				.andExpect(header().string("Content-Range", "bytes 990-999/1000")).andReturn();
		assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(slice(990, 999));
	}

	@Test
	void aRangeThatStartsAtZeroIsAPartialAnswerToo() throws Exception {
		stream(title, "bytes=0-0").andExpect(status().isPartialContent())
				.andExpect(header().string("Content-Range", "bytes 0-0/1000"))
				.andExpect(header().longValue("Content-Length", 1));
	}

	@Test
	void anEndBeyondTheFileIsCutAndStillAPartialAnswer() throws Exception {
		stream(title, "bytes=990-5000").andExpect(status().isPartialContent())
				.andExpect(header().string("Content-Range", "bytes 990-999/1000"));
	}

	@Test
	void seekingToTheMiddleDoesNotNeedTheStartOfTheFile() throws Exception {
		// What a player does when the viewer jumps ahead: ask for a later part only.
		MvcResult result = stream(title, "bytes=500-599").andExpect(status().isPartialContent()).andReturn();
		assertThat(result.getResponse().getContentAsByteArray()).hasSize(100).isEqualTo(slice(500, 599));
	}

	@Test
	void aBigFileIsSentInFull() throws Exception {
		byte[] big = new byte[300_000];
		for (int i = 0; i < big.length; i++) {
			big[i] = (byte) (i % 251);
		}
		Files.write(films.resolve("big.mp4"), big);
		UUID bigTitle = indexed("big.mp4");
		assertThat(stream(bigTitle).andReturn().getResponse().getContentAsByteArray()).isEqualTo(big);
		byte[] part = stream(bigTitle, "bytes=70000-199999").andReturn().getResponse().getContentAsByteArray();
		assertThat(part).hasSize(130_000);
		assertThat(part[0]).isEqualTo(big[70000]);
		assertThat(part[part.length - 1]).isEqualTo(big[199999]);
	}

	// ---- Ranges that cannot be served ----

	@Test
	void aStartAtTheEndOfTheFileIsNotSatisfiable() throws Exception {
		stream(title, "bytes=1000-").andExpect(status().isRequestedRangeNotSatisfiable())
				.andExpect(header().string("Content-Range", "bytes */1000"))
				.andExpect(jsonPath("$.error").value("range_not_satisfiable"))
				.andExpect(jsonPath("$.message").value("This part of the video is not available."));
		stream(title, "bytes=5000-6000").andExpect(status().isRequestedRangeNotSatisfiable())
				.andExpect(header().string("Content-Range", "bytes */1000"));
	}

	@Test
	void severalRangesAreNotSatisfiable() throws Exception {
		stream(title, "bytes=0-9,20-29").andExpect(status().isRequestedRangeNotSatisfiable())
				.andExpect(header().string("Content-Range", "bytes */1000"));
	}

	@Test
	void aMalformedRangeIsIgnoredAndTheWholeFileIsSent() throws Exception {
		stream(title, "bytes=abc").andExpect(status().isOk()).andExpect(header().longValue("Content-Length", 1000));
		stream(title, "items=0-5").andExpect(status().isOk());
	}

	// ---- Permission on every request (PLY-04) ----

	@Test
	void anonymousRequestsAreRefusedEvenForARange() throws Exception {
		mvc.perform(get("/media/" + title + "/stream")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
		mvc.perform(get("/media/" + title + "/stream").header("Range", "bytes=0-9"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anOrdinaryUserMayWatch() throws Exception {
		mvc.perform(get("/media/" + title + "/stream").header("X-Test-Caller", "user")).andExpect(status().isOk());
	}

	// ---- Errors the viewer can understand (PLY-05, I4) ----

	@Test
	void anUnknownTitleOrIdIsNotFound() throws Exception {
		stream(UUID.randomUUID()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("not_found"));
		mvc.perform(get("/media/not-an-id/stream").header("X-Test-Caller", "user")).andExpect(status().isNotFound());
	}

	@Test
	void aFileRemovedAfterIndexingIsAMissingFileNotAServerError() throws Exception {
		Files.delete(films.resolve("movie.mp4"));
		stream(title).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("file_not_found"))
				.andExpect(jsonPath("$.message").value(
						"The file for this title could not be found. It may have been moved or deleted from the library."));
	}

	@Test
	void aTitleStillDownloadingCannotBePlayedYet() throws Exception {
		UUID downloading = jdbc.queryForObject(
				"insert into media_item (title, availability) values ('Coming', 'DOWNLOADING') returning id", UUID.class);
		stream(downloading).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("media_not_available"))
				.andExpect(jsonPath("$.message").value("This title is still downloading and cannot be played yet."));
	}

	@Test
	void aTitleWithoutAFileIsAMissingFile() throws Exception {
		UUID gone = jdbc.queryForObject(
				"insert into media_item (title, availability) values ('Gone', 'UNAVAILABLE') returning id", UUID.class);
		stream(gone).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("file_not_found"));
	}

	@Test
	void aFilePathThatEscapesItsFolderIsDeniedAndNothingIsRevealed() throws Exception {
		Path secret = Files.writeString(mounted.resolve("secret.txt"), "top secret");
		Files.createSymbolicLink(films.resolve("leak.mp4"), secret);
		UUID leak = indexed("leak.mp4");

		MvcResult result = stream(leak).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("access_denied"))
				.andExpect(jsonPath("$.message").value("You do not have access to this video.")).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("secret").doesNotContain(mounted.toString());
	}

	@Test
	void aRelativePathWithDotDotCannotLeaveItsFolder() throws Exception {
		Files.writeString(mounted.resolve("outside.mp4"), "outside");
		// The database refuses such a path, so the guard is what is tested here: a stored path built by hand.
		UUID file = jdbc.queryForObject(
				"insert into media_file (root_id, relative_path, size_bytes) values (?, 'x.mp4', 1) returning id", UUID.class, root);
		jdbc.update("alter table media_file drop constraint media_file_path_no_traversal");
		try {
			jdbc.update("update media_file set relative_path = '../outside.mp4' where id = ?", file);
			UUID item = jdbc.queryForObject(
					"insert into media_item (media_file_id, title, availability) values (?, 'Out', 'AVAILABLE') returning id",
					UUID.class, file);
			stream(item).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("access_denied"));
		}
		finally {
			jdbc.update("delete from media_item");
			jdbc.update("delete from media_file where relative_path = '../outside.mp4'");
			jdbc.update("alter table media_file add constraint media_file_path_no_traversal "
					+ "check (relative_path !~ '(^|/)\\.\\.(/|$)')");
		}
	}

	@Test
	void aDirectoryIsNotAFile() throws Exception {
		Files.createDirectory(films.resolve("folder.mp4"));
		UUID folder = indexed("folder.mp4");
		stream(folder).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("file_not_found"));
	}

	@Test
	void aRootThatWasRemovedLeavesATitleThatCannotBePlayed() throws Exception {
		jdbc.update("update media_item set availability = 'UNAVAILABLE'");
		jdbc.update("delete from media_root where id = ?", root);
		stream(title).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("file_not_found"));
	}

	@Test
	void errorsNeverShowAPath() throws Exception {
		Files.delete(films.resolve("movie.mp4"));
		String body = stream(title).andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(mounted.toString()).doesNotContain("movie.mp4").doesNotContain("Exception");
	}

	// ---- Helpers ----

	private ResultActions stream(UUID id) throws Exception {
		return mvc.perform(request(id));
	}

	private ResultActions stream(UUID id, String range) throws Exception {
		return mvc.perform(request(id).header("Range", range));
	}

	private MockHttpServletRequestBuilder request(UUID id) {
		return get("/media/" + id + "/stream").header("X-Test-Caller", "user");
	}

	private UUID indexed(String relativePath) {
		UUID file = jdbc.queryForObject(
				"insert into media_file (root_id, relative_path, size_bytes) values (?, ?, 1) returning id", UUID.class,
				root, relativePath);
		return jdbc.queryForObject(
				"insert into media_item (media_file_id, title, availability) values (?, ?, 'AVAILABLE') returning id",
				UUID.class, file, relativePath);
	}

	private static byte[] slice(int from, int toInclusive) {
		return java.util.Arrays.copyOfRange(CONTENT, from, toInclusive + 1);
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
