package com.homeflix.library.catalogue;

import java.util.List;

/** One row of the home screen: a genre and its most recently added titles (CAT-02). */
public record Category(String genre, List<MediaSummary> items) {

}
