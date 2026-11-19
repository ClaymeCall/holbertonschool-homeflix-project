package com.homeflix.acquisition.catalogue;

import java.util.UUID;

/**
 * What the download service needs from an entry: its title, for the library, its download source, for the
 * torrent client, and the info hash of that magnet link, which names the torrent in the client before it is
 * even added. Internal to the service: never returned by the API (R-15).
 */
public record DownloadableEntry(UUID id, String title, String downloadSource, String infoHash) {

	@Override
	public String toString() {
		return "DownloadableEntry[id=" + id + ", title=" + title + "]";
	}

}
