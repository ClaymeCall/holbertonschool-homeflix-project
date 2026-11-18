package com.homeflix.library.catalogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads of the catalogue. Every value reaches the database as a bound parameter, never as part of the
 * SQL text (SEC-01), and the search uses {@code strpos} so a {@code %} or {@code _} typed by a user
 * means itself.
 */
@Repository
class CatalogueRepository {

	/** How many titles each home screen category shows. */
	static final int CATEGORY_SIZE = 20;

	private static final String ITEM_COLUMNS = "m.id, m.title, m.poster_url, m.duration_seconds, m.availability";

	private final JdbcTemplate jdbc;

	CatalogueRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<MediaSummary> search(CatalogueQuery query) {
		StringBuilder sql = new StringBuilder("select " + ITEM_COLUMNS + " from media_item m where true");
		List<Object> args = new ArrayList<>();
		if (query.text() != null) {
			sql.append(" and strpos(lower(m.title), lower(?)) > 0");
			args.add(query.text());
		}
		if (query.genre() != null) {
			sql.append(" and exists (select 1 from media_item_genre mg join genre g on g.id = mg.genre_id")
					.append(" where mg.media_item_id = m.id and lower(g.name) = lower(?))");
			args.add(query.genre());
		}
		sql.append(switch (query.sort()) {
			case RECENT -> " order by m.created_at desc, m.id";
			case TITLE -> " order by lower(m.title), m.id";
		});
		sql.append(" limit ? offset ?");
		args.add(query.limit());
		args.add(query.offset());

		List<Row> rows = jdbc.query(sql.toString(), (rs, n) -> new Row(rs.getObject("id", UUID.class),
				rs.getString("title"), rs.getString("poster_url"), (Integer) rs.getObject("duration_seconds"),
				rs.getString("availability")), args.toArray());
		return toSummaries(rows);
	}

	Optional<MediaDetails> find(UUID id) {
		List<Row> rows = jdbc.query("select " + ITEM_COLUMNS + ", m.description from media_item m where m.id = ?",
				(rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("poster_url"),
						(Integer) rs.getObject("duration_seconds"), rs.getString("availability"),
						rs.getString("description")),
				id);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Row row = rows.get(0);
		List<String> genres = genresOf(List.of(row.id())).getOrDefault(row.id(), List.of());
		return Optional.of(new MediaDetails(row.id(), row.title(), genres, row.posterUrl(), row.durationSeconds(),
				row.availability(), row.description()));
	}

	/** Genre names that have at least one title, alphabetical without regard to case. */
	List<String> genres() {
		return jdbc.queryForList("""
				select g.name from genre g
				where exists (select 1 from media_item_genre mg where mg.genre_id = g.id)
				order by lower(g.name), g.name
				""", String.class);
	}

	/** One category per genre that has titles, each with its most recently added titles. */
	List<Category> categories() {
		List<String> genreNames = genres();
		Map<String, List<Row>> byGenre = new LinkedHashMap<>();
		genreNames.forEach(name -> byGenre.put(name, new ArrayList<>()));
		jdbc.query("""
				select ranked.name, ranked.id, ranked.title, ranked.poster_url, ranked.duration_seconds, ranked.availability
				from (
				    select g.name, m.id, m.title, m.poster_url, m.duration_seconds, m.availability, m.created_at,
				           row_number() over (partition by g.id order by m.created_at desc, m.id) as position
				    from genre g
				    join media_item_genre mg on mg.genre_id = g.id
				    join media_item m on m.id = mg.media_item_id) ranked
				where ranked.position <= ?
				order by lower(ranked.name), ranked.name, ranked.created_at desc, ranked.id
				""", rs -> {
					byGenre.get(rs.getString("name")).add(new Row(rs.getObject("id", UUID.class),
							rs.getString("title"), rs.getString("poster_url"), (Integer) rs.getObject("duration_seconds"),
							rs.getString("availability")));
				}, CATEGORY_SIZE);

		List<UUID> ids = byGenre.values().stream().flatMap(List::stream).map(Row::id).distinct().toList();
		Map<UUID, List<String>> genresById = genresOf(ids);
		List<Category> categories = new ArrayList<>();
		byGenre.forEach((name, rows) -> categories.add(new Category(name, rows.stream()
				.map(row -> summary(row, genresById)).toList())));
		return categories;
	}

	private List<MediaSummary> toSummaries(List<Row> rows) {
		Map<UUID, List<String>> genresById = genresOf(rows.stream().map(Row::id).toList());
		return rows.stream().map(row -> summary(row, genresById)).toList();
	}

	private static MediaSummary summary(Row row, Map<UUID, List<String>> genresById) {
		return new MediaSummary(row.id(), row.title(), genresById.getOrDefault(row.id(), List.of()), row.posterUrl(),
				row.durationSeconds(), row.availability());
	}

	/** The genre names of several titles in one query, alphabetical for each title. */
	private Map<UUID, List<String>> genresOf(List<UUID> ids) {
		Map<UUID, List<String>> result = new HashMap<>();
		if (ids.isEmpty()) {
			return result;
		}
		jdbc.query("""
				select mg.media_item_id, g.name from media_item_genre mg
				join genre g on g.id = mg.genre_id
				where mg.media_item_id = any (?)
				order by lower(g.name), g.name
				""", rs -> {
					result.computeIfAbsent(rs.getObject("media_item_id", UUID.class), k -> new ArrayList<>())
							.add(rs.getString("name"));
				}, (Object) ids.toArray(UUID[]::new));
		return result;
	}

	private record Row(UUID id, String title, String posterUrl, Integer durationSeconds, String availability,
			String description) {

		Row(UUID id, String title, String posterUrl, Integer durationSeconds, String availability) {
			this(id, title, posterUrl, durationSeconds, availability, null);
		}

	}

}
