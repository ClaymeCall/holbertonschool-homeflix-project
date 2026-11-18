package com.homeflix.library.playback;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class StreamRepository {

	private final JdbcTemplate jdbc;

	StreamRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	Optional<StreamTarget> find(UUID mediaId) {
		return jdbc.query("""
				select m.availability, r.path as root_path, f.relative_path
				from media_item m
				left join media_file f on f.id = m.media_file_id
				left join media_root r on r.id = f.root_id
				where m.id = ?
				""", (rs, n) -> new StreamTarget(rs.getString("availability"), rs.getString("root_path"),
				rs.getString("relative_path")), mediaId).stream().findFirst();
	}

}
