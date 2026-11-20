package com.homeflix.acquisition.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where completed downloads are and how long the handoff to the library may take (J7;
 * docs/architecture/consistency-rules.md, "Time limit and visible failure").
 *
 * @param folder the downloads folder, mounted read-only in this service at the path the torrent client
 *        saves to ({@code /downloads}), so the client's content paths map directly to it
 * @param handoffTimeout how long a verified download may wait for the library before it fails with
 *        {@code handoff_failed} ({@code ACQUISITION_HANDOFF_TIMEOUT}, 5 minutes by default)
 */
@ConfigurationProperties("homeflix.downloads")
public record DownloadsProperties(
		@DefaultValue("/downloads") String folder,
		@DefaultValue("5m") Duration handoffTimeout) {

}
