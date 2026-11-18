package com.homeflix.library.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.homeflix.library.HeaderCallers;
import com.homeflix.library.TestcontainersConfiguration;

/** Search, genre filter, categories and details against a real PostgreSQL (CAT-01 to CAT-04, CAT-10). */
@Import({ TestcontainersConfiguration.class, HeaderCallers.class })
@SpringBootTest
class CatalogueEndpointsTests {

	private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-01-01T12:00:00Z");

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;
	private int minute;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbc.update("delete from media_item");
		jdbc.update("delete from genre");
		jdbc.update("delete from media_file");
		jdbc.update("delete from media_root");
		minute = 0;
	}

	// ---- Permissions (ACC-06) ----

	@Test
	void anonymousRequestsAreRefused() throws Exception {
		for (String path : new String[] { "/media", "/media/" + UUID.randomUUID(), "/categories", "/genres" }) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error").value("unauthenticated"));
		}
	}

	@Test
	void anOrdinaryUserCanReadTheCatalogue() throws Exception {
		item("Heat");
		for (String path : new String[] { "/media", "/categories", "/genres" }) {
			mvc.perform(get(path).header("X-Test-Caller", "user")).andExpect(status().isOk());
		}
	}

	// ---- List and search (CAT-04) ----

	@Test
	void anEmptyCatalogueGivesAnEmptyArray() throws Exception {
		as(get("/media")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void titlesAreSortedAlphabeticallyWithoutRegardToCaseByDefault() throws Exception {
		item("zebra");
		item("Alien");
		item("brazil");
		as(get("/media")).andExpect(jsonPath("$[*].title", org.hamcrest.Matchers.contains("Alien", "brazil", "zebra")));
	}

	@Test
	void searchFindsAPartOfATitleWithoutRegardToCase() throws Exception {
		item("The Great Escape");
		item("Great Expectations");
		item("Heat");
		as(get("/media").param("query", "GREAT")).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].title", org.hamcrest.Matchers.contains("Great Expectations", "The Great Escape")));
		as(get("/media").param("query", "scape")).andExpect(jsonPath("$.length()").value(1));
		as(get("/media").param("query", "nothing like it")).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void aBlankSearchListsEverything() throws Exception {
		item("A");
		item("B");
		as(get("/media").param("query", "   ")).andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void percentAndUnderscoreInASearchMeanThemselves() throws Exception {
		item("100% Wolf");
		item("100 Wolf");
		item("Some_Thing");
		item("SomeXThing");
		as(get("/media").param("query", "100%")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("100% Wolf"));
		as(get("/media").param("query", "e_T")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("Some_Thing"));
	}

	@Test
	void aSearchCannotInjectSql() throws Exception {
		item("Heat");
		as(get("/media").param("query", "' or 1=1 --")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		as(get("/media").param("query", "'; drop table media_item; --")).andExpect(status().isOk());
		as(get("/media").param("genre", "x' or 'a'='a")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		assertThat(count("media_item")).isEqualTo(1);
	}

	// ---- Genre filter (CAT-04) ----

	@Test
	void theGenreFilterKeepsOnlyTitlesOfThatGenreWithoutRegardToCase() throws Exception {
		item("Heat", "Action", "Crime");
		item("Alien", "Horror");
		item("Untagged");
		as(get("/media").param("genre", "action")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("Heat"));
		as(get("/media").param("genre", "Crime")).andExpect(jsonPath("$.length()").value(1));
		as(get("/media").param("genre", "Western")).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void theGenreFilterAndTheSearchCombine() throws Exception {
		item("Heat", "Action");
		item("Heatwave", "Drama");
		item("Rambo", "Action");
		as(get("/media").param("genre", "Action").param("query", "heat")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("Heat"));
	}

	@Test
	void aTitleWithSeveralGenresIsListedOnceWithAllOfThem() throws Exception {
		item("Heat", "Crime", "Action");
		as(get("/media").param("genre", "Action")).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].genres", org.hamcrest.Matchers.contains("Action", "Crime")));
	}

	// ---- Recently added and paging ----

	@Test
	void recentSortsNewestFirst() throws Exception {
		item("First");
		item("Second");
		item("Third");
		as(get("/media").param("sort", "recent"))
				.andExpect(jsonPath("$[*].title", org.hamcrest.Matchers.contains("Third", "Second", "First")));
		as(get("/media").param("sort", "title"))
				.andExpect(jsonPath("$[*].title", org.hamcrest.Matchers.contains("First", "Second", "Third")));
	}

	@Test
	void theLimitAndTheOffsetPageThroughTheList() throws Exception {
		for (int i = 1; i <= 5; i++) {
			item("Title " + i);
		}
		as(get("/media").param("limit", "2")).andExpect(jsonPath("$[*].title", org.hamcrest.Matchers.contains("Title 1", "Title 2")));
		as(get("/media").param("limit", "2").param("offset", "2"))
				.andExpect(jsonPath("$[*].title", org.hamcrest.Matchers.contains("Title 3", "Title 4")));
		as(get("/media").param("limit", "2").param("offset", "4")).andExpect(jsonPath("$.length()").value(1));
		as(get("/media").param("offset", "10")).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void aPageHoldsFiftyTitlesByDefaultAndNeverMoreThanAHundred() throws Exception {
		for (int i = 1; i <= 120; i++) {
			item(String.format("Title %03d", i));
		}
		as(get("/media")).andExpect(jsonPath("$.length()").value(50));
		as(get("/media").param("limit", "100")).andExpect(jsonPath("$.length()").value(100));
	}

	// ---- Validation (SEC-01) ----

	@Test
	void badParametersAreRefusedWithTheFieldNamed() throws Exception {
		item("Heat");
		refused(get("/media").param("limit", "0"), "limit");
		refused(get("/media").param("limit", "101"), "limit");
		refused(get("/media").param("limit", "many"), "limit");
		refused(get("/media").param("offset", "-1"), "offset");
		refused(get("/media").param("offset", "1.5"), "offset");
		refused(get("/media").param("sort", "random"), "sort");
		refused(get("/media").param("query", "x".repeat(101)), "query");
		refused(get("/media").param("genre", "x".repeat(51)), "genre");
	}

	@Test
	void severalBadParametersAreAllReportedAtOnce() throws Exception {
		as(get("/media").param("limit", "0").param("offset", "-3").param("sort", "x")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.limit").exists()).andExpect(jsonPath("$.fields.offset").exists())
				.andExpect(jsonPath("$.fields.sort").exists());
	}

	@Test
	void aTextAtTheLimitIsAccepted() throws Exception {
		as(get("/media").param("query", "x".repeat(100)).param("genre", "y".repeat(50)).param("limit", "100"))
				.andExpect(status().isOk());
	}

	// ---- Missing metadata and availability (CAT-01, CAT-10) ----

	@Test
	void aTitleWithoutMetadataIsListedWithNullValues() throws Exception {
		item("Bare");
		as(get("/media")).andExpect(jsonPath("$[0].title").value("Bare"))
				.andExpect(jsonPath("$[0].genres.length()").value(0))
				.andExpect(jsonPath("$[0].posterUrl").value((Object) null))
				.andExpect(jsonPath("$[0].durationSeconds").value((Object) null))
				.andExpect(jsonPath("$[0].availability").value("UNAVAILABLE"));
	}

	@Test
	void unavailableAndDownloadingTitlesAreListedAsSuch() throws Exception {
		UUID root = jdbc.queryForObject("insert into media_root (path) values ('/media/library') returning id", UUID.class);
		UUID file = jdbc.queryForObject(
				"insert into media_file (root_id, relative_path, size_bytes) values (?, 'a.mp4', 1) returning id", UUID.class, root);
		jdbc.update("insert into media_item (media_file_id, title, availability) values (?, 'Here', 'AVAILABLE')", file);
		jdbc.update("insert into media_item (title, availability) values ('Gone', 'UNAVAILABLE')");
		jdbc.update("insert into media_item (title, availability) values ('Coming', 'DOWNLOADING')");

		as(get("/media")).andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[?(@.title == 'Here')].availability").value("AVAILABLE"))
				.andExpect(jsonPath("$[?(@.title == 'Gone')].availability").value("UNAVAILABLE"))
				.andExpect(jsonPath("$[?(@.title == 'Coming')].availability").value("DOWNLOADING"));
	}

	// ---- Details (CAT-03) ----

	@Test
	void theDetailsHaveEverythingATitleHas() throws Exception {
		UUID id = item("Heat", "Crime", "Action");
		jdbc.update("update media_item set description = 'A heist.', poster_url = 'https://upload.wikimedia.org/heat.jpg', "
				+ "duration_seconds = 10200 where id = ?", id);
		as(get("/media/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.title").value("Heat"))
				.andExpect(jsonPath("$.description").value("A heist."))
				.andExpect(jsonPath("$.posterUrl").value("https://upload.wikimedia.org/heat.jpg"))
				.andExpect(jsonPath("$.durationSeconds").value(10200))
				.andExpect(jsonPath("$.genres", org.hamcrest.Matchers.contains("Action", "Crime")))
				.andExpect(jsonPath("$.availability").value("UNAVAILABLE"));
	}

	@Test
	void theDetailsOfATitleWithoutMetadataHaveNulls() throws Exception {
		UUID id = item("Bare");
		as(get("/media/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value((Object) null))
				.andExpect(jsonPath("$.posterUrl").value((Object) null))
				.andExpect(jsonPath("$.durationSeconds").value((Object) null))
				.andExpect(jsonPath("$.genres.length()").value(0));
	}

	@Test
	void anUnknownOrMalformedIdIsNotFound() throws Exception {
		as(get("/media/" + UUID.randomUUID())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));
		as(get("/media/not-an-id")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("not_found"));
	}

	// ---- Genres and categories (CAT-02) ----

	@Test
	void genresListsOnlyGenresThatHaveATitleAlphabetically() throws Exception {
		item("A", "drama", "Action");
		item("B", "Crime");
		jdbc.update("insert into genre (name) values ('Empty')");
		as(get("/genres")).andExpect(jsonPath("$", org.hamcrest.Matchers.contains("Action", "Crime", "drama")));
	}

	@Test
	void categoriesHaveOneRowPerGenreWithTheNewestTitlesFirst() throws Exception {
		item("Old Action", "Action");
		item("Mid Drama", "Drama");
		item("New Action", "Action");
		jdbc.update("insert into genre (name) values ('Empty')");

		as(get("/categories")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].genre").value("Action"))
				.andExpect(jsonPath("$[0].items[*].title", org.hamcrest.Matchers.contains("New Action", "Old Action")))
				.andExpect(jsonPath("$[1].genre").value("Drama"))
				.andExpect(jsonPath("$[1].items.length()").value(1))
				.andExpect(jsonPath("$[0].items[0].genres", org.hamcrest.Matchers.contains("Action")));
	}

	@Test
	void aCategoryHoldsTheTwentyMostRecentTitles() throws Exception {
		for (int i = 1; i <= 25; i++) {
			item("Title " + i, "Action");
		}
		as(get("/categories")).andExpect(jsonPath("$[0].items.length()").value(20))
				.andExpect(jsonPath("$[0].items[0].title").value("Title 25"))
				.andExpect(jsonPath("$[0].items[19].title").value("Title 6"));
	}

	@Test
	void aTitleWithSeveralGenresAppearsInEachCategory() throws Exception {
		item("Heat", "Action", "Crime");
		as(get("/categories")).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].items[0].title").value("Heat"))
				.andExpect(jsonPath("$[1].items[0].title").value("Heat"))
				.andExpect(jsonPath("$[1].items[0].genres", org.hamcrest.Matchers.contains("Action", "Crime")));
	}

	@Test
	void noCategoriesWhenNoTitleHasAGenre() throws Exception {
		item("Untagged");
		as(get("/categories")).andExpect(jsonPath("$.length()").value(0));
	}

	// ---- Helpers ----

	private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header("X-Test-Caller", "user"));
	}

	private void refused(MockHttpServletRequestBuilder request, String field) throws Exception {
		as(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields." + field).exists());
	}

	/** A title with its genres, each added a minute after the one before it. */
	private UUID item(String title, String... genres) {
		UUID id = jdbc.queryForObject(
				"insert into media_item (title, availability, created_at) values (?, 'UNAVAILABLE', ?) returning id",
				UUID.class, title, BASE.plusMinutes(minute++));
		for (String genre : genres) {
			UUID genreId = jdbc.query("select id from genre where lower(name) = lower(?)", (rs, n) -> rs.getObject("id", UUID.class),
					genre).stream().findFirst()
					.orElseGet(() -> jdbc.queryForObject("insert into genre (name) values (?) returning id", UUID.class, genre));
			jdbc.update("insert into media_item_genre values (?, ?)", id, genreId);
		}
		return id;
	}

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

}
