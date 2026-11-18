package com.homeflix.acquisition.downloads;

/** The four states the brief asks to show (ACQ-04). A cancelled download is FAILED with "cancelled". */
public enum DownloadState {
	QUEUED, DOWNLOADING, COMPLETED, FAILED;

	boolean active() {
		return this == QUEUED || this == DOWNLOADING;
	}
}
