package com.homeflix.acquisition.downloads;

import java.util.Optional;

/**
 * Translates a qBittorrent torrent state into one of the four states the administrator sees (ACQ-04,
 * ADR-0014). States are those of the qBittorrent 5 Web API ({@code torrents/info}, field {@code state}).
 *
 * <ul>
 * <li><b>QUEUED</b>: nothing is transferring yet. Waiting in the client's queue, stopped, checking or
 * allocating files, or fetching the metadata of a magnet link from peers.</li>
 * <li><b>DOWNLOADING</b>: transferring, or waiting for peers (stalled). A torrent the client has completed
 * also stays DOWNLOADING, at 100 %: the job is COMPLETED only after the checks and the handoff to the
 * library (J7; consistency rules, "Time limit and visible failure").</li>
 * <li><b>FAILED</b>: the client reports an error, or the downloaded files have disappeared.</li>
 * </ul>
 *
 * A state this class does not know (a newer qBittorrent, or {@code unknown}) changes nothing.
 */
final class StateMapping {

	/** What a job becomes for a torrent state: its state, and its progress from 0 to 100. */
	record Status(DownloadState state, int progressPercent) {
	}

	private StateMapping() {
	}

	/**
	 * @param qbittorrentState the {@code state} field of qBittorrent
	 * @param progress the {@code progress} field of qBittorrent, from 0 to 1
	 * @return the job's new status, or empty to leave the job as it is
	 */
	static Optional<Status> map(String qbittorrentState, double progress) {
		int percent = percent(progress);
		DownloadState state = switch (qbittorrentState == null ? "" : qbittorrentState) {
			case "queuedDL", "stoppedDL", "pausedDL", "checkingDL", "checkingResumeData", "allocating", "metaDL",
					"forcedMetaDL" -> DownloadState.QUEUED;
			case "downloading", "forcedDL", "stalledDL", "moving" -> DownloadState.DOWNLOADING;
			// Complete in the client: waits at 100 % for the checks and the handoff (J7).
			case "uploading", "stalledUP", "queuedUP", "stoppedUP", "pausedUP", "forcedUP", "checkingUP" -> {
				percent = 100;
				yield DownloadState.DOWNLOADING;
			}
			case "error", "missingFiles" -> DownloadState.FAILED;
			default -> null;
		};
		return state == null ? Optional.empty() : Optional.of(new Status(state, percent));
	}

	/** Rounded down, so 100 % means the whole file is there. */
	static int percent(double progress) {
		if (Double.isNaN(progress) || progress <= 0) {
			return 0;
		}
		if (progress >= 1) {
			return 100;
		}
		return Math.min(99, (int) Math.floor(progress * 100));
	}

}
