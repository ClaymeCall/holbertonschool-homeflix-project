package com.homeflix.acquisition.catalogue;

import java.util.UUID;

/**
 * What the download service needs from an entry: its title, for the library, and its download source,
 * for the torrent client. Internal to the service: never returned by the API (R-15).
 */
public record DownloadableEntry(UUID id, String title, String downloadSource) {

	@Override
	public String toString() {
		return "DownloadableEntry[id=" + id + ", title=" + title + "]";
	}

}
