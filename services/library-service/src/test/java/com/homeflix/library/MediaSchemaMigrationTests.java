package com.homeflix.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the V2 media migration against a real PostgreSQL (ADR-0011).
 * Each test runs in a transaction that is rolled back, so tests stay independent.
 * PostgreSQL aborts the transaction after the first error, so each test triggers
 * at most one failing statement, and it is the last one.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MediaSchemaMigrationTests {

	@Autowired
	private JdbcTemplate jdbc;

	// ---- Migration ----

	@Test
	void migrationIsAppliedAndStartsEmpty() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where version = '2' and success", Integer.class);
		assertThat(applied).isEqualTo(1);
		for (String table : List.of("media_root", "media_file", "media_item", "genre", "media_item_genre",
				"favourite", "progress")) {
			assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class))
					.as(table).isZero();
		}
	}

	@Test
	void noForeignKeyPointsToAnotherServiceOrToAProfile() {
		// profile_id and acquisition_entry_id are plain values (ADR-0006).
		List<String> foreignKeyColumns = jdbc.queryForList("""
				select kcu.table_name || '.' || kcu.column_name
				from information_schema.table_constraints tc
				join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
				where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = 'public'
				""", String.class);
		assertThat(foreignKeyColumns).doesNotContain("favourite.profile_id", "progress.profile_id",
				"media_item.acquisition_entry_id");
		assertThat(foreignKeyColumns).contains("favourite.media_item_id", "progress.media_item_id",
				"media_file.root_id", "media_item.media_file_id");
	}

	// ---- Metadata may be missing (CAT-10) ----

	@Test
	void titleWithOnlyATitleCanBeStored() {
		UUID file = insertFile(insertRoot("/media/library"), "Alone.mp4");
		Map<String, Object> row = jdbc.queryForMap(
				"insert into media_item (media_file_id, title, availability) values (?, 'Alone', 'AVAILABLE') returning *",
				file);

		assertThat(row.get("id")).isInstanceOf(UUID.class);
		assertThat(row.get("description")).isNull();
		assertThat(row.get("poster_url")).isNull();
		assertThat(row.get("duration_seconds")).isNull();
		assertThat(row.get("created_at")).isNotNull();
		assertThat(row.get("updated_at")).isNotNull();
	}

	@Test
	void titleWithoutAGenreIsStillListedByAGenreFreeQuery() {
		insertItem("No genre", "UNAVAILABLE", null);
		assertThat(jdbc.queryForObject("""
				select count(*) from media_item m
				where not exists (select 1 from media_item_genre g where g.media_item_id = m.id)
				""", Integer.class)).isEqualTo(1);
	}

	@Test
	void blankTitleIsRejected() {
		assertThatThrownBy(() -> insertItem("   ", "UNAVAILABLE", null))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("media_item_title_not_blank");
	}

	@Test
	void posterMustBeHttps() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into media_item (title, availability, poster_url) values ('P', 'UNAVAILABLE', 'http://example.org/p.jpg')"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("media_item_poster_https");
	}

	@Test
	void httpsPosterIsAccepted() {
		jdbc.update("insert into media_item (title, availability, poster_url) values ('P', 'UNAVAILABLE', 'https://upload.wikimedia.org/p.jpg')");
		assertThat(jdbc.queryForObject("select poster_url from media_item", String.class)).startsWith("https://");
	}

	@Test
	void negativeDurationIsRejected() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into media_item (title, availability, duration_seconds) values ('D', 'UNAVAILABLE', -1)"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("media_item_duration_positive");
	}

	// ---- Availability (CAT-01) ----

	@Test
	void availabilityAcceptsAvailableUnavailableAndDownloading() {
		UUID file = insertFile(insertRoot("/media/library"), "A.mp4");
		insertItem("Available", "AVAILABLE", file);
		insertItem("Unavailable", "UNAVAILABLE", null);
		insertItem("Downloading", "DOWNLOADING", null);

		assertThat(jdbc.queryForList("select availability from media_item order by availability", String.class))
				.containsExactly("AVAILABLE", "DOWNLOADING", "UNAVAILABLE");
	}

	@Test
	void unknownAvailabilityIsRejected() {
		assertThatThrownBy(() -> insertItem("Odd", "BROKEN", null))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("media_item_availability_valid");
	}

	@Test
	void availabilityIsRequired() {
		assertThatThrownBy(() -> jdbc.update("insert into media_item (title) values ('No state')"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("availability");
	}

	@Test
	void availableTitleNeedsAFile() {
		assertThatThrownBy(() -> insertItem("No file", "AVAILABLE", null))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("media_item_available_has_file");
	}

	@Test
	void downloadingTitleCannotHaveAFileYet() {
		UUID file = insertFile(insertRoot("/media/downloads"), "D.mp4");
		assertThatThrownBy(() -> insertItem("Downloading", "DOWNLOADING", file))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("media_item_downloading_no_file");
	}

	// ---- Duplicate-free scans (LIB-05) ----

	@Test
	void sameRootAndRelativePathIsRejected() {
		UUID root = insertRoot("/media/library");
		insertFile(root, "Films/A.mp4");

		assertThatThrownBy(() -> insertFile(root, "Films/A.mp4"))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("media_file_path_unique");
	}

	@Test
	void sameRelativePathInAnotherRootIsAllowed() {
		insertFile(insertRoot("/media/library"), "A.mp4");
		insertFile(insertRoot("/media/downloads"), "A.mp4");
		assertThat(jdbc.queryForObject("select count(*) from media_file", Integer.class)).isEqualTo(2);
	}

	@Test
	void rootPathMustBeUnique() {
		insertRoot("/media/library");
		assertThatThrownBy(() -> insertRoot("/media/library"))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("media_root_path_unique");
	}

	@Test
	void relativePathCannotBeAbsoluteOrBlank() {
		UUID root = insertRoot("/media/library");
		assertThatThrownBy(() -> insertFile(root, "/etc/passwd"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("media_file_path_relative");
	}

	@Test
	void missingFileStaysInTheTable() {
		UUID file = insertFile(insertRoot("/media/library"), "A.mp4");
		jdbc.update("update media_file set missing_since = now() where id = ?", file);
		assertThat(jdbc.queryForObject("select missing_since is not null from media_file where id = ?", Boolean.class, file)).isTrue();
	}

	@Test
	void aFileBacksAtMostOneItem() {
		UUID file = insertFile(insertRoot("/media/library"), "A.mp4");
		insertItem("First", "AVAILABLE", file);
		assertThatThrownBy(() -> insertItem("Second", "AVAILABLE", file))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("media_item_file_unique");
	}

	@Test
	void anAcquisitionEntryCreatesAtMostOneItem() {
		UUID entry = UUID.randomUUID();
		jdbc.update("insert into media_item (title, availability, acquisition_entry_id) values ('T', 'DOWNLOADING', ?)", entry);
		assertThatThrownBy(() -> jdbc.update(
				"insert into media_item (title, availability, acquisition_entry_id) values ('T again', 'DOWNLOADING', ?)", entry))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("media_item_acquisition_unique");
	}

	// ---- Removing a root keeps the catalogue (LIB-07) ----

	@Test
	void removingARootKeepsTheTitleItsFavouritesAndItsProgress() {
		UUID root = insertRoot("/media/library");
		UUID file = insertFile(root, "A.mp4");
		UUID item = insertItem("Keeper", "AVAILABLE", file);
		UUID profile = UUID.randomUUID();
		jdbc.update("insert into favourite (profile_id, media_item_id) values (?, ?)", profile, item);
		jdbc.update("insert into progress (profile_id, media_item_id, position_seconds, duration_seconds) values (?, ?, 60, 600)", profile, item);

		// The service marks the title UNAVAILABLE first, then removes the root.
		jdbc.update("update media_item set availability = 'UNAVAILABLE' where id = ?", item);
		jdbc.update("delete from media_root where id = ?", root);

		assertThat(jdbc.queryForObject("select count(*) from media_file", Integer.class)).isZero();
		Map<String, Object> kept = jdbc.queryForMap("select * from media_item where id = ?", item);
		assertThat(kept.get("title")).isEqualTo("Keeper");
		assertThat(kept.get("media_file_id")).isNull();
		assertThat(kept.get("availability")).isEqualTo("UNAVAILABLE");
		assertThat(jdbc.queryForObject("select count(*) from favourite", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from progress", Integer.class)).isEqualTo(1);
	}

	@Test
	void removingARootWhileATitleIsStillAvailableIsRefused() {
		UUID root = insertRoot("/media/library");
		insertItem("Still available", "AVAILABLE", insertFile(root, "A.mp4"));

		assertThatThrownBy(() -> jdbc.update("delete from media_root where id = ?", root))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("media_item_available_has_file");
	}

	// ---- Genres (CAT-02, CAT-04) ----

	@Test
	void aTitleCanHaveSeveralGenres() {
		UUID item = insertItem("Multi", "UNAVAILABLE", null);
		UUID drama = insertGenre("Drama");
		UUID comedy = insertGenre("Comedy");
		jdbc.update("insert into media_item_genre values (?, ?), (?, ?)", item, drama, item, comedy);

		assertThat(jdbc.queryForList("""
				select g.name from media_item_genre l join genre g on g.id = l.genre_id
				where l.media_item_id = ? order by g.name
				""", String.class, item)).containsExactly("Comedy", "Drama");
	}

	@Test
	void genreNamesAreUniqueWithoutRegardToCase() {
		insertGenre("Drama");
		assertThatThrownBy(() -> insertGenre("drama"))
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("genre_name_lower_unique");
	}

	@Test
	void genreNameCannotHaveSurroundingSpaces() {
		assertThatThrownBy(() -> insertGenre(" Drama"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("genre_name_trimmed");
	}

	@Test
	void sameGenreCannotBeLinkedTwice() {
		UUID item = insertItem("Once", "UNAVAILABLE", null);
		UUID genre = insertGenre("Drama");
		jdbc.update("insert into media_item_genre values (?, ?)", item, genre);
		assertThatThrownBy(() -> jdbc.update("insert into media_item_genre values (?, ?)", item, genre))
				.isInstanceOf(DuplicateKeyException.class);
	}

	// ---- Favourites and progress (PRF-03, CAT-08, PLY-07 to PLY-09) ----

	@Test
	void profileIdIsAPlainValueWithNoRowToPointTo() {
		UUID item = insertItem("Fav", "UNAVAILABLE", null);
		jdbc.update("insert into favourite (profile_id, media_item_id) values (?, ?)", UUID.randomUUID(), item);
		assertThat(jdbc.queryForObject("select count(*) from favourite", Integer.class)).isEqualTo(1);
	}

	@Test
	void aProfileFavouritesATitleOnlyOnce() {
		UUID item = insertItem("Fav", "UNAVAILABLE", null);
		UUID profile = UUID.randomUUID();
		jdbc.update("insert into favourite (profile_id, media_item_id) values (?, ?)", profile, item);
		assertThatThrownBy(() -> jdbc.update("insert into favourite (profile_id, media_item_id) values (?, ?)", profile, item))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void twoProfilesKeepSeparateProgressForTheSameTitle() {
		UUID item = insertItem("Shared", "UNAVAILABLE", null);
		jdbc.update("insert into progress (profile_id, media_item_id, position_seconds, duration_seconds) values (?, ?, 100, 600)", UUID.randomUUID(), item);
		jdbc.update("insert into progress (profile_id, media_item_id, position_seconds, duration_seconds) values (?, ?, 500, 600)", UUID.randomUUID(), item);
		assertThat(jdbc.queryForObject("select count(*) from progress", Integer.class)).isEqualTo(2);
	}

	@Test
	void progressHasOneRowPerProfileAndTitle() {
		UUID item = insertItem("Once", "UNAVAILABLE", null);
		UUID profile = UUID.randomUUID();
		jdbc.update("insert into progress (profile_id, media_item_id, position_seconds, duration_seconds) values (?, ?, 10, 600)", profile, item);
		assertThatThrownBy(() -> jdbc.update(
				"insert into progress (profile_id, media_item_id, position_seconds, duration_seconds) values (?, ?, 20, 600)", profile, item))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void positionCannotBeBeyondTheDuration() {
		UUID item = insertItem("Short", "UNAVAILABLE", null);
		assertThatThrownBy(() -> jdbc.update(
				"insert into progress (profile_id, media_item_id, position_seconds, duration_seconds) values (?, ?, 700, 600)",
				UUID.randomUUID(), item))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("progress_position_valid");
	}

	@Test
	void durationMustBePositive() {
		UUID item = insertItem("Zero", "UNAVAILABLE", null);
		assertThatThrownBy(() -> jdbc.update(
				"insert into progress (profile_id, media_item_id, position_seconds, duration_seconds) values (?, ?, 0, 0)",
				UUID.randomUUID(), item))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("progress_duration_positive");
	}

	@Test
	void removingACatalogueEntryDeletesItsGenreLinksFavouritesAndProgress() {
		UUID item = insertItem("Gone", "UNAVAILABLE", null);
		UUID profile = UUID.randomUUID();
		jdbc.update("insert into media_item_genre values (?, ?)", item, insertGenre("Drama"));
		jdbc.update("insert into favourite (profile_id, media_item_id) values (?, ?)", profile, item);
		jdbc.update("insert into progress (profile_id, media_item_id, position_seconds, duration_seconds) values (?, ?, 5, 60)", profile, item);

		jdbc.update("delete from media_item where id = ?", item);

		assertThat(jdbc.queryForObject("select count(*) from media_item_genre", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from favourite", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from progress", Integer.class)).isZero();
		// The genre itself and any source file are not deleted.
		assertThat(jdbc.queryForObject("select count(*) from genre", Integer.class)).isEqualTo(1);
	}

	// ---- Helpers ----

	private UUID insertRoot(String path) {
		return jdbc.queryForObject("insert into media_root (path) values (?) returning id", UUID.class, path);
	}

	private UUID insertFile(UUID root, String relativePath) {
		return jdbc.queryForObject(
				"insert into media_file (root_id, relative_path, size_bytes) values (?, ?, 1024) returning id",
				UUID.class, root, relativePath);
	}

	private UUID insertItem(String title, String availability, UUID file) {
		return jdbc.queryForObject(
				"insert into media_item (media_file_id, title, availability) values (?, ?, ?) returning id",
				UUID.class, file, title, availability);
	}

	private UUID insertGenre(String name) {
		return jdbc.queryForObject("insert into genre (name) values (?) returning id", UUID.class, name);
	}

}
