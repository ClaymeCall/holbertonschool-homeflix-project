package com.homeflix.acquisition.catalogue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Reads the verified catalogue. There is no write method: entries are added only by reviewed migrations
 * (data model, "The verified catalogue"; ACQ-09).
 */
@Repository
class EntryRepository {

	private static final String COLUMNS = """
			id, title, source_name, source_url, licence, licence_url, attribution,
			expected_size_bytes, expected_format""";

	private static final RowMapper<Entry> ENTRY = (rs, row) -> new Entry(
			rs.getObject("id", UUID.class),
			rs.getString("title"),
			rs.getString("source_name"),
			rs.getString("source_url"),
			rs.getString("licence"),
			rs.getString("licence_url"),
			rs.getString("attribution"),
			rs.getLong("expected_size_bytes"),
			rs.getString("expected_format"));

	private final JdbcTemplate jdbc;

	EntryRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<Entry> findAll() {
		return jdbc.query("select " + COLUMNS + " from acquisition_entry order by lower(title), id", ENTRY);
	}

	Optional<Entry> find(UUID id) {
		return jdbc.query("select " + COLUMNS + " from acquisition_entry where id = ?", ENTRY, id)
				.stream().findFirst();
	}

}
