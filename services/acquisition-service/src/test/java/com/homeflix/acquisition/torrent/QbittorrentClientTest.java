package com.homeflix.acquisition.torrent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The session cookie of qBittorrent 5 ({@code QBT_SID_<port>}) and of earlier versions ({@code SID}). */
class QbittorrentClientTest {

	@Test
	void recognisesTheSessionCookieOfEachVersion() {
		assertThat(QbittorrentClient.isSessionCookie("QBT_SID_8080=abc")).isTrue();
		assertThat(QbittorrentClient.isSessionCookie("SID=abc")).isTrue();
		assertThat(QbittorrentClient.isSessionCookie("SIDE=abc")).isFalse();
		assertThat(QbittorrentClient.isSessionCookie("other=abc")).isFalse();
	}

}
