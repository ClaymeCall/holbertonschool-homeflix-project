package com.homeflix.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The verified catalogue table (V2) and its entries (V3, V7, V8, V9) against a real PostgreSQL (ADR-0011):
 * what the database refuses, so a bad entry cannot be added even by a migration (ACQ-02, R-15, R-19).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AcquisitionEntryMigrationTests {

	private static final String VALID_MAGNET = "magnet:?xt=urn:btih:" + "a".repeat(40);

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		// Only the rows this class inserted: the migrated entries are kept.
		jdbc.update("delete from acquisition_entry where source_name = 'Test source'");
	}

	@Test
	void sintelIsTheFirstVerifiedEntry() {
		Map<String, Object> sintel = jdbc.queryForMap("select * from acquisition_entry where title = 'Sintel'");

		assertThat(sintel.get("licence")).isEqualTo("Creative Commons Attribution 3.0 Unported (CC BY 3.0)");
		assertThat(sintel.get("licence_url")).isEqualTo("https://creativecommons.org/licenses/by/3.0/");
		assertThat(sintel.get("source_url")).isEqualTo("https://durian.blender.org/");
		assertThat(sintel.get("expected_size_bytes")).isEqualTo(129184608L);
		assertThat(sintel.get("expected_format")).isEqualTo("MP4");
		// The torrent built for the demo, recorded with issue J1 (#74).
		assertThat((String) sintel.get("download_source"))
				.startsWith("magnet:?xt=urn:btih:4e626263b96d72a36b1b963f44188bf937004d5f&");
	}

	@Test
	void bigBuckBunnyAndTearsOfSteelAreVerifiedEntries() {
		Map<String, Object> bunny = jdbc.queryForMap("select * from acquisition_entry where title = 'Big Buck Bunny'");
		Map<String, Object> tears = jdbc.queryForMap("select * from acquisition_entry where title = 'Tears of Steel'");

		assertThat(bunny.get("source_url")).isEqualTo("https://peach.blender.org/about/");
		assertThat(tears.get("source_url")).isEqualTo("https://mango.blender.org/sharing/");
		for (Map<String, Object> entry : List.of(bunny, tears)) {
			assertThat(entry.get("licence")).isEqualTo("Creative Commons Attribution 3.0 Unported (CC BY 3.0)");
			assertThat(entry.get("licence_url")).isEqualTo("https://creativecommons.org/licenses/by/3.0/");
			assertThat(entry.get("expected_format")).isEqualTo("MP4");
		}
		assertThat(bunny.get("expected_size_bytes")).isEqualTo(259392822L);
		assertThat(tears.get("expected_size_bytes")).isEqualTo(505533124L);
		// The single-file torrents built for HomeFlix (#184), not the published multi-file ones.
		assertThat((String) bunny.get("download_source"))
				.startsWith("magnet:?xt=urn:btih:7b28c6b6cfc3eaf6b546fb2256c6d9c883f316c3&");
		assertThat((String) tears.get("download_source"))
				.startsWith("magnet:?xt=urn:btih:e6d8867d826b6406746e067e8b08d582cbb9dee8&");
	}

	@Test
	void springAndSpriteFrightAreVerifiedEntries() {
		Map<String, Object> spring = jdbc.queryForMap("select * from acquisition_entry where title = 'Spring'");
		Map<String, Object> sprite = jdbc.queryForMap("select * from acquisition_entry where title = 'Sprite Fright'");

		assertThat(spring.get("source_url")).isEqualTo("https://studio.blender.org/films/spring/");
		assertThat(sprite.get("source_url")).isEqualTo("https://studio.blender.org/films/sprite-fright/");
		for (Map<String, Object> entry : List.of(spring, sprite)) {
			assertThat(entry.get("licence")).isEqualTo("Creative Commons Attribution 4.0 International (CC BY 4.0)");
			assertThat(entry.get("licence_url")).isEqualTo("https://creativecommons.org/licenses/by/4.0/");
			assertThat(entry.get("expected_format")).isEqualTo("MP4");
		}
		// CC BY asks to say when the work was changed: Spring was remuxed.
		assertThat((String) spring.get("attribution")).contains("Remuxed");
		assertThat(spring.get("expected_size_bytes")).isEqualTo(90190400L);
		assertThat(sprite.get("expected_size_bytes")).isEqualTo(110581245L);
		// The single-file torrents built for HomeFlix (#198).
		assertThat((String) spring.get("download_source"))
				.startsWith("magnet:?xt=urn:btih:df418f62342353102d8c74e3f66f65643bd60a47&");
		assertThat((String) sprite.get("download_source"))
				.startsWith("magnet:?xt=urn:btih:d219709e3db88021f22b26ba21ee5aeffcab3b54&");
	}

	@Test
	void sevenMoreBlenderOpenMoviesAreVerifiedEntries() {
		// Title, licence version, expected size and info hash of the single-file torrent built for HomeFlix (#198).
		Object[][] expected = {
				{ "Caminandes 3: Llamigos", "3.0", 138945811L, "13179b286c170a89d226f83724ced32bd40919d4" },
				{ "Charge", "4.0", 191560304L, "5175c58aa5334f23bad19f3c79d4c493f255e597" },
				{ "Coffee Run", "4.0", 17475696L, "909a2a7aa6560cc4de8bb8285a8b480ff12514da" },
				{ "Cosmos Laundromat", "4.0", 220087074L, "e8b8a9f4b82bc070aafb9c50348c85719c35e5e7" },
				{ "Elephants Dream", "2.5", 67801890L, "247ad6ae086248ee40e8bf537cbb5ae1b8c9bebf" },
				{ "Hero", "4.0", 53008533L, "11eb33acc6693933c7c7bc946a3b6c5e73e51a34" },
				{ "Wing It!", "4.0", 65577791L, "3f6cc49e3fc6fc2fc0e7734590973a76ed3103fe" } };
		for (Object[] film : expected) {
			Map<String, Object> entry = jdbc.queryForMap("select * from acquisition_entry where title = ?", film[0]);

			assertThat((String) entry.get("licence")).contains("(CC BY " + film[1] + ")");
			assertThat(entry.get("licence_url")).isEqualTo("https://creativecommons.org/licenses/by/" + film[1] + "/");
			assertThat((String) entry.get("source_url")).startsWith("https://");
			assertThat(entry.get("expected_format")).isEqualTo("MP4");
			assertThat(entry.get("expected_size_bytes")).isEqualTo(film[2]);
			assertThat((String) entry.get("download_source")).startsWith("magnet:?xt=urn:btih:" + film[3] + "&");
		}
		// CC BY asks to say when the work was changed: these four were remuxed.
		for (String remuxed : List.of("Coffee Run", "Cosmos Laundromat", "Hero", "Wing It!")) {
			assertThat(jdbc.queryForObject("select attribution from acquisition_entry where title = ?", String.class,
					remuxed)).contains("Remuxed");
		}
	}

	@Test
	void aValidEntryIsAccepted() {
		assertThatCode(() -> insert("Test entry one", "https://example.org/", "https://example.org/licence?lang=en",
				VALID_MAGNET, 1000, "MP4")).doesNotThrowAnyException();
		assertThatCode(() -> insert("Test entry two", "https://example.org", "https://example.org/licence#by",
				"magnet:?xt=urn:btih:" + "b".repeat(40) + "&dn=film.mp4&tr=udp%3A%2F%2Ftracker.example.org%3A1337&tr=https%3A%2F%2Ftracker.example.org%2Fannounce",
				1000, "MP4")).doesNotThrowAnyException();
	}

	@Test
	void sourceAndLicencePagesMustBeHttps() {
		assertRefused(() -> insert("A", "http://example.org/", "https://example.org/licence", VALID_MAGNET, 1000, "MP4"),
				"acquisition_entry_source_url_https");
		assertRefused(() -> insert("A", "https://example.org/", "http://example.org/licence", VALID_MAGNET, 1000, "MP4"),
				"acquisition_entry_licence_url_https");
		for (String page : new String[] { "https://", "https:///path", "https://example.org/a page" }) {
			assertRefused(() -> insert("A", page, "https://example.org/licence", VALID_MAGNET, 1000, "MP4"),
					"acquisition_entry_source_url_https");
			assertRefused(() -> insert("A", "https://example.org/", page, VALID_MAGNET, 1000, "MP4"),
					"acquisition_entry_licence_url_https");
		}
	}

	@Test
	void theDownloadSourceIsAMagnetLinkWithANameAndTrackersOnly() {
		String magnet = VALID_MAGNET;
		for (String source : new String[] {
				"https://example.org/film.mp4",
				"https://example.org/film.torrent",
				"http://example.org/film.torrent",
				"magnet:?xt=urn:btih:tooshort",
				"magnet:?xt=urn:btih:" + "A".repeat(40),
				"magnet:?dn=film.mp4",
				magnet + "&xs=http%3A%2F%2Finternal-host%2Ffilm.torrent",
				magnet + "&ws=file%3A%2F%2F%2Fetc%2Fpasswd",
				magnet + "&as=https%3A%2F%2Fexample.org%2Ffilm.mp4",
				magnet + "&dn=film.mp4&xs=http%3A%2F%2Finternal-host",
				magnet + "&dn=",
				magnet + "&dn=a film.mp4",
				magnet + "&",
				magnet + "#tr=x",
				"file:///etc/passwd",
				"/downloads/film.mp4" }) {
			assertRefused(() -> insert("A", "https://example.org/", "https://example.org/licence", source, 1000, "MP4"),
					"acquisition_entry_download_source_valid");
		}
	}

	@Test
	void theExpectedSizeMustBePositive() {
		assertRefused(() -> insert("A", "https://example.org/", "https://example.org/licence", VALID_MAGNET, 0, "MP4"),
				"acquisition_entry_size_positive");
	}

	@Test
	void onlyTheSupportedFormatIsAccepted() {
		assertRefused(() -> insert("A", "https://example.org/", "https://example.org/licence", VALID_MAGNET, 1000, "MKV"),
				"acquisition_entry_format_valid");
	}

	@Test
	void aTitleOrAnInfoHashCannotBeListedTwice() {
		insert("Test entry", "https://example.org/", "https://example.org/licence", VALID_MAGNET, 1000, "MP4");

		assertRefused(() -> insert("TEST ENTRY", "https://example.org/", "https://example.org/licence",
				"magnet:?xt=urn:btih:" + "b".repeat(40), 1000, "MP4"), "acquisition_entry_title_lower_unique");
		assertRefused(() -> insert("Another title", "https://example.org/", "https://example.org/licence",
				VALID_MAGNET, 1000, "MP4"), "acquisition_entry_info_hash_unique");
		// The same content with another display name or other trackers is still the same info hash.
		assertRefused(() -> insert("Another title", "https://example.org/", "https://example.org/licence",
				VALID_MAGNET + "&dn=other.mp4&tr=udp%3A%2F%2Ftracker.example.org%3A1337", 1000, "MP4"),
				"acquisition_entry_info_hash_unique");
		// The Sintel entry of V3 too.
		assertRefused(() -> insert("Another title", "https://example.org/", "https://example.org/licence",
				"magnet:?xt=urn:btih:4e626263b96d72a36b1b963f44188bf937004d5f", 1000, "MP4"),
				"acquisition_entry_info_hash_unique");
	}

	private void insert(String title, String sourceUrl, String licenceUrl, String downloadSource, long size,
			String format) {
		jdbc.update("""
				insert into acquisition_entry (title, source_name, source_url, licence, licence_url,
				    download_source, expected_size_bytes, expected_format)
				values (?, 'Test source', ?, 'Test licence', ?, ?, ?, ?)
				""", title, sourceUrl, licenceUrl, downloadSource, size, format);
	}

	private static void assertRefused(Runnable insert, String constraint) {
		assertThatThrownBy(insert::run)
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining(constraint);
	}

}
