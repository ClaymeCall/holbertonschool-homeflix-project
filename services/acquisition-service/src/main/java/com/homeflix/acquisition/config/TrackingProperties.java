package com.homeflix.acquisition.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The status check of downloads (J6; docs/architecture/consistency-rules.md, "Acquisition handoff").
 *
 * @param enabled false to stop the periodic check (tests call it directly)
 * @param interval time between two checks, 10 s by default as the consistency rules say
 * @param missingGrace how long a new job may have no torrent in the client before it fails, which leaves
 *        time for the start that is adding it
 */
@ConfigurationProperties("homeflix.downloads.tracking")
public record TrackingProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("10s") Duration interval,
		@DefaultValue("60s") Duration missingGrace) {

}
