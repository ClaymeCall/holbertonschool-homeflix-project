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
		jdbc.update("delete from acquisition_entry where title <> 'Sintel'");
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
				.startsWith("magnet:?xt=urn:btih:338806f8ce250642f0525949dcb3be6ccdc9b430&");
	}

	@Test
	void aValidEntryIsAccepted() {
		assertThatCode(() -> insert("Big Buck Bunny", "https://peach.blender.org/", "https://creativecommons.org/licenses/by/3.0/",
				VALID_MAGNET, 1000, "MP4")).doesNotThrowAnyException();
		assertThatCode(() -> insert("Tears of Steel", "https://mango.blender.org/", "https://creativecommons.org/licenses/by/3.0/",
				"https://example.org/tears-of-steel.torrent", 1000, "MP4")).doesNotThrowAnyException();
	}

	@Test
	void sourceAndLicencePagesMustBeHttps() {
		assertRefused(() -> insert("A", "http://example.org/", "https://example.org/licence", VALID_MAGNET, 1000, "MP4"),
				"acquisition_entry_source_url_https");
		assertRefused(() -> insert("A", "https://example.org/", "http://example.org/licence", VALID_MAGNET, 1000, "MP4"),
				"acquisition_entry_licence_url_https");
	}

	@Test
	void theDownloadSourceIsAMagnetLinkOrATorrentAddressOnly() {
		for (String source : new String[] {
				"https://example.org/film.mp4",
				"http://example.org/film.torrent",
				"magnet:?xt=urn:btih:tooshort",
				"magnet:?dn=film.mp4",
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
	void aTitleOrADownloadSourceCannotBeListedTwice() {
		insert("Big Buck Bunny", "https://example.org/", "https://example.org/licence", VALID_MAGNET, 1000, "MP4");

		assertRefused(() -> insert("big buck bunny", "https://example.org/", "https://example.org/licence",
				"magnet:?xt=urn:btih:" + "b".repeat(40), 1000, "MP4"), "acquisition_entry_title_lower_unique");
		assertRefused(() -> insert("Another title", "https://example.org/", "https://example.org/licence",
				VALID_MAGNET, 1000, "MP4"), "acquisition_entry_download_source_unique");
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
