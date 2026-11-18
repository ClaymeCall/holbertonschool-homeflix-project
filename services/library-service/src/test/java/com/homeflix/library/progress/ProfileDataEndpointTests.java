package com.homeflix.library.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

import com.homeflix.library.HeaderCallers;
import com.homeflix.library.TestcontainersConfiguration;

/** Favourites and viewing progress of a profile (PRF-03, PRF-04, PLY-07 to PLY-10), against PostgreSQL. */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class ProfileDataEndpointTests {

	private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private UUID film;
	private UUID other;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from favourite");
		jdbc.update("delete from progress");
		jdbc.update("delete from media_item");
		film = title("Film");
		other = title("Other");
	}

	// ---- Who may call (PRF-04) ----

	@Test
	void anonymousRequestsAreRefusedEverywhere() throws Exception {
		for (String path : new String[] { "/progress", "/progress/" + film, "/favourites" }) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("unauthenticated"));
		}
		mvc.perform(put("/progress/" + film).contentType(MediaType.APPLICATION_JSON).content(body(10, 100)))
				.andExpect(status().isUnauthorized());
		mvc.perform(put("/favourites/" + film)).andExpect(status().isUnauthorized());
		mvc.perform(delete("/progress/" + film)).andExpect(status().isUnauthorized());
		mvc.perform(delete("/favourites/" + film)).andExpect(status().isUnauthorized());
	}

	@Test
	void aSignedInAccountWithoutAProfileMustChooseOne() throws Exception {
		String[][] calls = { { "GET", "/progress" }, { "GET", "/progress/" + film }, { "PUT", "/progress/" + film },
				{ "DELETE", "/progress/" + film }, { "GET", "/favourites" }, { "PUT", "/favourites/" + film },
				{ "DELETE", "/favourites/" + film } };
		for (String[] call : calls) {
			MockHttpServletRequestBuilder request = switch (call[0]) {
				case "GET" -> get(call[1]);
				case "PUT" -> put(call[1]).contentType(MediaType.APPLICATION_JSON).content(body(10, 100));
				default -> delete(call[1]);
			};
			mvc.perform(request.header("X-Test-Caller", "user").header("X-Test-Profile", "none"))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("profile_not_selected"))
					.andExpect(jsonPath("$.message").value("Choose a profile first."));
		}
	}

	@Test
	void anAdministratorHasNoExtraAccessToAProfile() throws Exception {
		// An administrator token without a profile gets the same refusal, and one with a profile sees only its own.
		mvc.perform(get("/progress").header("X-Test-Caller", "admin")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("profile_not_selected"));
	}

	// ---- Progress ----

	@Test
	void savingCreatesTheEntryAndComputesItsState() throws Exception {
		save(ALICE, film, 120, 1000).andExpect(status().isOk()).andExpect(jsonPath("$.mediaId").value(film.toString()))
				.andExpect(jsonPath("$.positionSeconds").value(120)).andExpect(jsonPath("$.durationSeconds").value(1000))
				.andExpect(jsonPath("$.state").value("STARTED")).andExpect(jsonPath("$.updatedAt").exists())
				.andExpect(jsonPath("$.media").doesNotExist());
	}

	@Test
	void savingAgainReplacesTheSingleEntry() throws Exception {
		save(ALICE, film, 120, 1000).andExpect(status().isOk());
		save(ALICE, film, 950, 1000).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("COMPLETED"));
		assertThat(jdbc.queryForObject("select count(*) from progress", Integer.class)).isEqualTo(1);
		as(ALICE, get("/progress/" + film)).andExpect(status().isOk()).andExpect(jsonPath("$.positionSeconds").value(950));
	}

	@Test
	void theFirstFewSecondsHaveNoState() throws Exception {
		save(ALICE, film, 3, 1000).andExpect(status().isOk()).andExpect(jsonPath("$.state").doesNotExist());
	}

	@Test
	void aTitleWithNoSavedProgressIsNotFound() throws Exception {
		as(ALICE, get("/progress/" + film)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void progressOfAnUnknownTitleIsNotFound() throws Exception {
		save(ALICE, UUID.randomUUID(), 10, 100).andExpect(status().isNotFound());
		mvc.perform(put("/progress/not-an-id").header("X-Test-Caller", "user").header("X-Test-Profile", ALICE.toString())
				.contentType(MediaType.APPLICATION_JSON).content(body(10, 100))).andExpect(status().isNotFound());
	}

	@Test
	void aBadPositionOrDurationIsRefusedAndNamesTheField() throws Exception {
		save(ALICE, film, -1, 100).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields.positionSeconds").value("The position cannot be negative."));
		save(ALICE, film, 10, 0).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.durationSeconds").value("The duration must be at least 1 second."));
		save(ALICE, film, 101, 100).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.positionSeconds").value("The position cannot be after the end."));
		as(ALICE, put("/progress/" + film).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.positionSeconds").exists())
				.andExpect(jsonPath("$.fields.durationSeconds").exists());
		as(ALICE, put("/progress/" + film).contentType(MediaType.APPLICATION_JSON).content("not json"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
		assertThat(jdbc.queryForObject("select count(*) from progress", Integer.class)).isZero();
	}

	@Test
	void theWholeTitleIsAllowedAsPosition() throws Exception {
		save(ALICE, film, 100, 100).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("COMPLETED"));
	}

	@Test
	void resettingRemovesTheEntryAndIsIdempotent() throws Exception {
		save(ALICE, film, 120, 1000).andExpect(status().isOk());
		as(ALICE, delete("/progress/" + film)).andExpect(status().isNoContent());
		as(ALICE, get("/progress/" + film)).andExpect(status().isNotFound());
		as(ALICE, delete("/progress/" + film)).andExpect(status().isNoContent());
		as(ALICE, delete("/progress/" + UUID.randomUUID())).andExpect(status().isNoContent());
	}

	@Test
	void theListIsMostRecentFirstAndCarriesTheTitle() throws Exception {
		save(ALICE, film, 120, 1000).andExpect(status().isOk());
		save(ALICE, other, 950, 1000).andExpect(status().isOk());
		as(ALICE, get("/progress")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].mediaId").value(other.toString())).andExpect(jsonPath("$[0].media.title").value("Other"))
				.andExpect(jsonPath("$[0].media.availability").value("UNAVAILABLE"))
				.andExpect(jsonPath("$[1].mediaId").value(film.toString())).andExpect(jsonPath("$[1].media.title").value("Film"));
		save(ALICE, film, 130, 1000).andExpect(status().isOk());
		as(ALICE, get("/progress")).andExpect(jsonPath("$[0].mediaId").value(film.toString()));
	}

	@Test
	void theListCanBeFilteredByState() throws Exception {
		UUID third = title("Third");
		save(ALICE, film, 120, 1000).andExpect(status().isOk());
		save(ALICE, other, 950, 1000).andExpect(status().isOk());
		save(ALICE, third, 2, 1000).andExpect(status().isOk());
		as(ALICE, get("/progress?state=STARTED")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].mediaId").value(film.toString()));
		as(ALICE, get("/progress?state=COMPLETED")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].mediaId").value(other.toString()));
		as(ALICE, get("/progress")).andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void aBadFilterOrPageIsRefused() throws Exception {
		as(ALICE, get("/progress?state=DONE")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.state").value("Use STARTED or COMPLETED."));
		as(ALICE, get("/progress?limit=0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.limit").exists());
		as(ALICE, get("/progress?limit=101")).andExpect(status().isBadRequest());
		as(ALICE, get("/progress?offset=-1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.offset").exists());
	}

	@Test
	void theListIsPaged() throws Exception {
		for (int i = 0; i < 3; i++) {
			save(ALICE, title("T" + i), 100, 1000).andExpect(status().isOk());
		}
		as(ALICE, get("/progress?limit=2")).andExpect(jsonPath("$.length()").value(2));
		as(ALICE, get("/progress?limit=2&offset=2")).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void progressSurvivesWhenTheTitleIsRemovedFromTheListOnlyIfTheTitleExists() throws Exception {
		save(ALICE, film, 120, 1000).andExpect(status().isOk());
		jdbc.update("delete from media_item where id = ?", film);
		as(ALICE, get("/progress")).andExpect(jsonPath("$.length()").value(0));
		assertThat(jdbc.queryForObject("select count(*) from progress", Integer.class)).isZero();
	}

	// ---- Isolation between profiles (PRF-03, PRF-04, AS-05) ----

	@Test
	void aProfileNeverSeesAnotherProfilesProgress() throws Exception {
		save(ALICE, film, 120, 1000).andExpect(status().isOk());
		as(BOB, get("/progress")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		as(BOB, get("/progress/" + film)).andExpect(status().isNotFound());
		save(BOB, film, 700, 1000).andExpect(status().isOk());
		as(ALICE, get("/progress/" + film)).andExpect(jsonPath("$.positionSeconds").value(120));
		as(BOB, delete("/progress/" + film)).andExpect(status().isNoContent());
		as(ALICE, get("/progress/" + film)).andExpect(status().isOk()).andExpect(jsonPath("$.positionSeconds").value(120));
	}

	@Test
	void aProfileIdInTheRequestIsIgnored() throws Exception {
		save(ALICE, film, 120, 1000).andExpect(status().isOk());
		// Only the token says whose data it is: a profile id sent as a parameter or in the body changes nothing.
		as(BOB, get("/progress?profileId=" + ALICE)).andExpect(jsonPath("$.length()").value(0));
		as(BOB, put("/progress/" + film + "?profileId=" + ALICE).contentType(MediaType.APPLICATION_JSON)
				.content("{\"positionSeconds\":5,\"durationSeconds\":10,\"profileId\":\"" + ALICE + "\"}"))
				.andExpect(status().isOk());
		as(ALICE, get("/progress/" + film)).andExpect(jsonPath("$.positionSeconds").value(120));
	}

	// ---- Favourites ----

	@Test
	void addingATitleListsItAndAddingAgainChangesNothing() throws Exception {
		as(ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
		as(ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from favourite", Integer.class)).isEqualTo(1);
		as(ALICE, get("/favourites")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(film.toString())).andExpect(jsonPath("$[0].title").value("Film"));
	}

	@Test
	void theListIsMostRecentlyAddedFirst() throws Exception {
		as(ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
		jdbc.update("update favourite set added_at = now() - interval '1 hour'");
		as(ALICE, put("/favourites/" + other)).andExpect(status().isNoContent());
		as(ALICE, get("/favourites")).andExpect(jsonPath("$[0].id").value(other.toString()))
				.andExpect(jsonPath("$[1].id").value(film.toString()));
	}

	@Test
	void anAddedTitleKeepsItsPlaceWhenAddedAgain() throws Exception {
		as(ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
		jdbc.update("update favourite set added_at = now() - interval '1 hour'");
		as(ALICE, put("/favourites/" + other)).andExpect(status().isNoContent());
		as(ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
		as(ALICE, get("/favourites")).andExpect(jsonPath("$[0].id").value(other.toString()));
	}

	@Test
	void removingIsIdempotent() throws Exception {
		as(ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
		as(ALICE, delete("/favourites/" + film)).andExpect(status().isNoContent());
		as(ALICE, delete("/favourites/" + film)).andExpect(status().isNoContent());
		as(ALICE, delete("/favourites/" + UUID.randomUUID())).andExpect(status().isNoContent());
		as(ALICE, get("/favourites")).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void anUnknownTitleCannotBeAdded() throws Exception {
		as(ALICE, put("/favourites/" + UUID.randomUUID())).andExpect(status().isNotFound());
		as(ALICE, put("/favourites/not-an-id")).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select count(*) from favourite", Integer.class)).isZero();
	}

	@Test
	void aProfileNeverSeesOrChangesAnotherProfilesList() throws Exception {
		as(ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
		as(BOB, get("/favourites")).andExpect(jsonPath("$.length()").value(0));
		as(BOB, put("/favourites/" + other)).andExpect(status().isNoContent());
		as(BOB, delete("/favourites/" + film)).andExpect(status().isNoContent());
		as(ALICE, get("/favourites")).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(film.toString()));
		as(BOB, get("/favourites")).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(other.toString()));
	}

	@Test
	void deletingATitleRemovesItFromEveryList() throws Exception {
		as(ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
		save(ALICE, film, 100, 1000).andExpect(status().isOk());
		jdbc.update("delete from media_item where id = ?", film);
		as(ALICE, get("/favourites")).andExpect(jsonPath("$.length()").value(0));
		assertThat(jdbc.queryForObject("select count(*) from favourite", Integer.class)).isZero();
	}

	@Test
	void favouritesAndProgressPersistInTheDatabase() throws Exception {
		// PLY-10: the data is in PostgreSQL, so it survives a restart of the service.
		as(ALICE, put("/favourites/" + film)).andExpect(status().isNoContent());
		save(ALICE, film, 100, 1000).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from favourite where profile_id = ?", Integer.class, ALICE)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select position_seconds from progress where profile_id = ?", Integer.class, ALICE))
				.isEqualTo(100);
	}

	// ---- Helpers ----

	private ResultActions save(UUID profile, UUID media, int position, int duration) throws Exception {
		return as(profile, put("/progress/" + media).contentType(MediaType.APPLICATION_JSON).content(body(position, duration)));
	}

	private ResultActions as(UUID profile, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("X-Test-Caller", "user").header("X-Test-Profile", profile.toString()));
	}

	private static String body(int position, int duration) {
		return "{\"positionSeconds\":" + position + ",\"durationSeconds\":" + duration + "}";
	}

	private UUID title(String name) {
		return jdbc.queryForObject("insert into media_item (title, availability) values (?, 'UNAVAILABLE') returning id",
				UUID.class, name);
	}

}
