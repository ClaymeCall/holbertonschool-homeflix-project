package com.homeflix.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The verified catalogue table (V2) and its first entry (V3) against a real PostgreSQL (ADR-0011):
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
