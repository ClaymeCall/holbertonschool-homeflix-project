package com.homeflix.acquisition.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * How the acquisition service reaches the torrent client's Web API (ADR-0014, ADR-0023): over the private
 * p2p network, with the login qBittorrent is configured with. The service refuses to start without it.
 */
@Validated
@ConfigurationProperties("homeflix.qbittorrent")
public record QbittorrentProperties(
		@NotBlank String url,
		@NotBlank(message = "QBITTORRENT_USERNAME is not set. Set it in .env.") String username,
		@NotBlank(message = "QBITTORRENT_PASSWORD is not set. Set it in .env.") String password) {

	@Override
	public String toString() {
		return "QbittorrentProperties[url=" + url + ", username=" + username + ", password=***]";
	}

}
