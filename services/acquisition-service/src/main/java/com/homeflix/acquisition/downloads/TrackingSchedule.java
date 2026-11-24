package com.homeflix.acquisition.downloads;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the status check at start-up and then every {@code homeflix.downloads.tracking.interval} (10 s).
 * Turned off with {@code homeflix.downloads.tracking.enabled=false}, as the tests do.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "homeflix.downloads.tracking.enabled", havingValue = "true", matchIfMissing = true)
class TrackingSchedule {

	private final DownloadTracker tracker;

	TrackingSchedule(DownloadTracker tracker) {
		this.tracker = tracker;
	}

	@Scheduled(fixedDelayString = "${homeflix.downloads.tracking.interval:10s}")
	void check() {
		tracker.check();
	}

}
