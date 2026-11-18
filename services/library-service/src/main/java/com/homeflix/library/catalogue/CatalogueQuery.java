package com.homeflix.library.catalogue;

/** A validated search of the catalogue. {@code text} and {@code genre} are null when not used. */
record CatalogueQuery(String text, String genre, Sort sort, int limit, int offset) {

	enum Sort {
		/** Newest first, for "recently added". */
		RECENT,
		/** Alphabetical, without regard to case. */
		TITLE
	}

}
