package com.homeflix.library.progress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The started and completed rules of PLY-09, on a title of 1000 seconds. */
class ProgressStateTest {

	@Test
	void fiveSecondsOrLessIsNeitherStartedNorCompleted() {
		assertThat(ProgressState.of(0, 1000)).isNull();
		assertThat(ProgressState.of(5, 1000)).isNull();
	}

	@Test
	void moreThanFiveSecondsIsStarted() {
		assertThat(ProgressState.of(6, 1000)).isEqualTo(ProgressState.STARTED);
		assertThat(ProgressState.of(500, 1000)).isEqualTo(ProgressState.STARTED);
		assertThat(ProgressState.of(899, 1000)).isEqualTo(ProgressState.STARTED);
	}

	@Test
	void ninetyPercentOrMoreIsCompleted() {
		assertThat(ProgressState.of(900, 1000)).isEqualTo(ProgressState.COMPLETED);
		assertThat(ProgressState.of(999, 1000)).isEqualTo(ProgressState.COMPLETED);
		assertThat(ProgressState.of(1000, 1000)).isEqualTo(ProgressState.COMPLETED);
	}

	@Test
	void aShortTitleIsCompletedBeforeItIsStarted() {
		// 4 seconds long: watching 4 seconds is 100 %, so it is completed even though 5 seconds were not reached.
		assertThat(ProgressState.of(4, 4)).isEqualTo(ProgressState.COMPLETED);
		assertThat(ProgressState.of(3, 4)).isNull();
	}

	@Test
	void hugeValuesDoNotOverflow() {
		assertThat(ProgressState.of(Integer.MAX_VALUE, Integer.MAX_VALUE)).isEqualTo(ProgressState.COMPLETED);
		assertThat(ProgressState.of(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)).isEqualTo(ProgressState.COMPLETED);
		assertThat(ProgressState.of(1_000_000_000, Integer.MAX_VALUE)).isEqualTo(ProgressState.STARTED);
	}

}
