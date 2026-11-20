package com.homeflix.acquisition.downloads;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import com.homeflix.acquisition.torrent.QbittorrentClient.Torrent;

/** The translation of qBittorrent's states into the four visible states (J6, ACQ-04, ADR-0014). */
class StateMappingTest {

	@ParameterizedTest(name = "{0} at {1} -> {2} {3} %")
	@CsvSource({
			// Nothing transferring yet.
			"queuedDL, 0, QUEUED, 0",
			"stoppedDL, 0.25, QUEUED, 25",
			"pausedDL, 0.25, QUEUED, 25",
			"checkingDL, 0.5, QUEUED, 50",
			"checkingResumeData, 0, QUEUED, 0",
			"allocating, 0, QUEUED, 0",
			"metaDL, 0, QUEUED, 0",
			"forcedMetaDL, 0, QUEUED, 0",
			// Transferring, or waiting for peers.
			"downloading, 0.423, DOWNLOADING, 42",
			"forcedDL, 0.1, DOWNLOADING, 10",
			"stalledDL, 0.7, DOWNLOADING, 70",
			"moving, 0.99, DOWNLOADING, 99",
			// Complete in the client: waits at 100 % for the checks and the handoff (J7).
			"uploading, 1, DOWNLOADING, 100",
			"stalledUP, 1, DOWNLOADING, 100",
			"queuedUP, 1, DOWNLOADING, 100",
			"stoppedUP, 1, DOWNLOADING, 100",
			"pausedUP, 1, DOWNLOADING, 100",
			"forcedUP, 1, DOWNLOADING, 100",
			"checkingUP, 0.98, DOWNLOADING, 100",
			// The client reports a failure.
			"error, 0.3, FAILED, 30",
			"missingFiles, 1, FAILED, 100" })
	void mapsEachStateOfTheClient(String qbittorrentState, double progress, DownloadState state, int percent) {
		assertThat(StateMapping.map(qbittorrentState, progress)).contains(new StateMapping.Status(state, percent));
	}

	@ParameterizedTest
	@ValueSource(strings = { "unknown", "aNewStateOfALaterVersion", "" })
	void anUnknownStateLeavesTheJobAsItIs(String qbittorrentState) {
		assertThat(StateMapping.map(qbittorrentState, 0.5)).isEmpty();
		assertThat(StateMapping.map(null, 0.5)).isEmpty();
	}

	@Test
	void progressIsRoundedDownSo100MeansTheWholeFile() {
		assertThat(StateMapping.percent(0.999)).isEqualTo(99);
		assertThat(StateMapping.percent(1)).isEqualTo(100);
		assertThat(StateMapping.percent(1.2)).isEqualTo(100);
		assertThat(StateMapping.percent(0.0049)).isZero();
		assertThat(StateMapping.percent(-1)).isZero();
		assertThat(StateMapping.percent(Double.NaN)).isZero();
	}

	@Test
	void everyFailureHasAPlainMessage() {
		assertThat(FailureMessages.forCode(null)).isNull();
		assertThat(FailureMessages.forCode("cancelled")).isEqualTo("This download was cancelled.");
		assertThat(FailureMessages.forCode("client_error"))
				.isEqualTo("The download service could not download this title. You can start it again.");
		assertThat(FailureMessages.forCode("torrent_missing"))
				.isEqualTo("This download is no longer in the download service. You can start it again.");
		assertThat(FailureMessages.forCode("verification_failed"))
				.isEqualTo("The downloaded file is not the expected video, so it was deleted. You can start it again.");
		assertThat(FailureMessages.forCode("handoff_failed"))
				.isEqualTo("The video was downloaded but could not be added to the library. You can start it again.");
		assertThat(FailureMessages.forCode("a_later_code")).isEqualTo("This download failed. You can start it again.");
	}

	@Test
	void theJobOfATorrentComesFromItsTag() {
		UUID job = UUID.randomUUID();
		assertThat(DownloadTracker.jobOf(new Torrent("h", "queuedDL", 0, "homeflix-job-" + job))).contains(job);
		assertThat(DownloadTracker.jobOf(new Torrent("h", "queuedDL", 0, "other, homeflix-job-" + job))).contains(job);
		assertThat(DownloadTracker.jobOf(new Torrent("h", "queuedDL", 0, "homeflix-job-not-a-uuid"))).isEmpty();
		assertThat(DownloadTracker.jobOf(new Torrent("h", "queuedDL", 0, "seeding"))).isEmpty();
		assertThat(DownloadTracker.jobOf(new Torrent("h", "queuedDL", 0, ""))).isEmpty();
		assertThat(DownloadTracker.jobOf(new Torrent("h", "queuedDL", 0, null))).isEmpty();
	}

}
