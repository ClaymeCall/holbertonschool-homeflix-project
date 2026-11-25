package com.homeflix.library.profiledata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.library.HeaderCallers;
import com.homeflix.library.TestcontainersConfiguration;

/**
 * Deleting the data of a profile, called by the identity service (K3, SEC-07, PRF-04, R-13, TST-05), against a
 * real PostgreSQL and a real file.
 */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class ProfileDataDeletionTests {

	private static final String IDENTITY_TOKEN = "test-identity-token-not-a-secret";
	private static final String ACQUISITION_TOKEN = "test-acquisition-token-not-a-secret";

	private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

	@TempDir
	Path folder;

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private UUID film;
	private UUID other;
	private Path videoFile;

	@BeforeEach
	void setUp() throws IOException {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from favourite");
		jdbc.update("delete from progress");
		jdbc.update("delete from media_item");
		jdbc.update("delete from media_root");

		// A title with a real file, and one without, both with data of two profiles.
		videoFile = Files.writeString(folder.resolve("Film.mp4"), "video");
		UUID root = jdbc.queryForObject("insert into media_root (path) values (?) returning id", UUID.class,
				folder.toString());
		UUID file = jdbc.queryForObject(
				"insert into media_file (root_id, relative_path, size_bytes) values (?, 'Film.mp4', 5) returning id",
				UUID.class, root);
		film = jdbc.queryForObject(
				"insert into media_item (title, availability, media_file_id) values ('Film', 'AVAILABLE', ?) returning id",
				UUID.class, file);
		other = jdbc.queryForObject(
				"insert into media_item (title, availability) values ('Other', 'UNAVAILABLE') returning id", UUID.class);
		for (UUID profile : new UUID[] { ALICE, BOB }) {
			for (UUID title : new UUID[] { film, other }) {
				jdbc.update("insert into favourite (profile_id, media_item_id) values (?, ?)", profile, title);
				jdbc.update("""
						insert into progress (profile_id, media_item_id, position_seconds, duration_seconds)
						values (?, ?, 10, 100)""", profile, title);
			}
		}
	}

	// ---- Who may call (ADR-0022) ----

	@Test
	void onlyTheIdentityServiceCanCall() throws Exception {
		// No secret, a wrong one, the acquisition service's secret, and a signed-in administrator: all refused.
		mvc.perform(delete("/internal/profiles/{id}/data", ALICE)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthenticated"));
		call(ALICE, "wrong").andExpect(status().isUnauthorized());
		call(ALICE, "").andExpect(status().isUnauthorized());
		call(ALICE, ACQUISITION_TOKEN).andExpect(status().isUnauthorized());
		mvc.perform(delete("/internal/profiles/{id}/data", ALICE).header("X-Test-Caller", "admin"))
				.andExpect(status().isUnauthorized());
		mvc.perform(delete("/internal/profiles/{id}/data", ALICE).header("X-Test-Caller", "user")
				.header("X-Test-Profile", ALICE.toString())).andExpect(status().isUnauthorized());

		assertThat(count("favourite")).isEqualTo(4);
		assertThat(count("progress")).isEqualTo(4);
	}

	@Test
	void theAcquisitionServiceCannotUseTheIdentityServicesSecretEither() throws Exception {
		// The identity secret opens only this endpoint, never the acquisition ones.
		mvc.perform(delete("/internal/profiles/{id}/data", ALICE).header("X-Service-Token", ACQUISITION_TOKEN))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/internal/downloads/{id}/failed", UUID.randomUUID()).header("X-Service-Token", IDENTITY_TOKEN))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aMalformedIdIsRefusedForTheSecretBeforeItIsRejectedAsUnknown() throws Exception {
		mvc.perform(delete("/internal/profiles/{id}/data", "not-an-id")).andExpect(status().isUnauthorized());
		mvc.perform(delete("/internal/profiles/{id}/data", "not-an-id").header("X-Service-Token", IDENTITY_TOKEN))
				.andExpect(status().isNotFound());
	}

	// ---- What is deleted ----

	@Test
	void allTheFavouritesAndTheProgressOfTheProfileAreRemoved() throws Exception {
		call(ALICE, IDENTITY_TOKEN).andExpect(status().isNoContent());

		assertThat(count("favourite where profile_id = ?", ALICE)).isZero();
		assertThat(count("progress where profile_id = ?", ALICE)).isZero();
	}

	@Test
	void nothingElseIsRemoved() throws Exception {
		call(ALICE, IDENTITY_TOKEN).andExpect(status().isNoContent());

		// The other profile keeps everything.
		assertThat(count("favourite where profile_id = ?", BOB)).isEqualTo(2);
		assertThat(count("progress where profile_id = ?", BOB)).isEqualTo(2);
		// The titles, their file records and the folder are still there.
		assertThat(count("media_item")).isEqualTo(2);
		assertThat(count("media_file")).isEqualTo(1);
		assertThat(count("media_root")).isEqualTo(1);
	}

	@Test
	void theSourceMediaIsNeverDeleted() throws Exception {
		call(ALICE, IDENTITY_TOKEN).andExpect(status().isNoContent());
		call(BOB, IDENTITY_TOKEN).andExpect(status().isNoContent());

		// Every profile's data is gone, yet the video file and its title remain (SEC-07, R-03).
		assertThat(count("favourite")).isZero();
		assertThat(count("progress")).isZero();
		assertThat(videoFile).exists().hasContent("video");
		assertThat(jdbc.queryForObject("select availability from media_item where id = ?", String.class, film))
				.isEqualTo("AVAILABLE");
	}

	@Test
	void repeatingTheCallOrDeletingAProfileWithNoDataIsNotAnError() throws Exception {
		call(ALICE, IDENTITY_TOKEN).andExpect(status().isNoContent());
		call(ALICE, IDENTITY_TOKEN).andExpect(status().isNoContent());
		call(UUID.randomUUID(), IDENTITY_TOKEN).andExpect(status().isNoContent());

		assertThat(count("favourite")).isEqualTo(2);
	}

	@Test
	void anIdThatIsNotAnIdIsNotFound() throws Exception {
		mvc.perform(delete("/internal/profiles/{id}/data", "not-an-id").header("X-Service-Token", IDENTITY_TOKEN))
				.andExpect(status().isNotFound());
		assertThat(count("favourite")).isEqualTo(4);
	}

	// ---- Helpers ----

	private ResultActions call(UUID profile, String token) throws Exception {
		return mvc.perform(delete("/internal/profiles/{id}/data", profile).header("X-Service-Token", token));
	}

	private int count(String from, Object... args) {
		return jdbc.queryForObject("select count(*) from " + from, Integer.class, args);
	}

}
