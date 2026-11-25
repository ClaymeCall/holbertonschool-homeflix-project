package com.homeflix.library.profiledata;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes what a profile owns in the library: its favourites and its viewing progress, in one transaction, so a
 * failure never leaves half of it (R-13, SEC-07). Nothing else is touched: titles, files and root folders stay.
 */
@Service
class ProfileDataService {

	private static final Logger log = LoggerFactory.getLogger(ProfileDataService.class);

	private final JdbcTemplate jdbc;

	ProfileDataService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Idempotent: a profile with no data, or one that never existed, is not an error. */
	@Transactional
	void deleteAllOf(UUID profileId) {
		int favourites = jdbc.update("delete from favourite where profile_id = ?", profileId);
		int progress = jdbc.update("delete from progress where profile_id = ?", profileId);
		log.info("Deleted the data of a profile: {} favourites, {} progress entries", favourites, progress);
	}

}
