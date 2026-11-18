package com.homeflix.library.progress;

/** Where a title stands for a profile. Computed from the saved position, never stored (PLY-09). */
public enum ProgressState {

	/** More than 5 seconds watched and less than 90 %. */
	STARTED,

	/** 90 % or more. */
	COMPLETED;

	static final int MIN_SECONDS = 5;

	/** @return the state, or null when 5 seconds or less were watched */
	static ProgressState of(int positionSeconds, int durationSeconds) {
		if ((long) positionSeconds * 10 >= (long) durationSeconds * 9) {
			return COMPLETED;
		}
		return positionSeconds > MIN_SECONDS ? STARTED : null;
	}

}
