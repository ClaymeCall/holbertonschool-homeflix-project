package com.homeflix.library.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MediaFileClassifierTest {

	@ParameterizedTest
	@ValueSource(strings = { "movie.mp4", "Movie.MP4", "my.film.2020.Mp4", "été (2019).mp4" })
	void mp4FilesAreSupportedWhateverTheCase(String name) {
		assertThat(MediaFileClassifier.classify(name)).isEqualTo(MediaFileKind.SUPPORTED_VIDEO);
	}

	@ParameterizedTest
	@ValueSource(strings = { "movie.mkv", "movie.avi", "MOVIE.MKV", "clip.mov", "clip.webm", "stream.ts", "film.hevc" })
	void otherVideoContainersAreUnsupported(String name) {
		assertThat(MediaFileClassifier.classify(name)).isEqualTo(MediaFileKind.UNSUPPORTED_VIDEO);
	}

	@Test
	void unsupportedVideosReportAFormatNotSupportedOutcome() {
		assertThat(MediaFileKind.UNSUPPORTED_VIDEO.errorCode()).isEqualTo("format_not_supported");
		assertThat(MediaFileKind.SUPPORTED_VIDEO.errorCode()).isNull();
		assertThat(MediaFileKind.IGNORED.errorCode()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "movie.srt", "poster.jpg", "movie.nfo", "notes.txt", "movie.mp4.part",
			"movie.mkv.!qB", "movie.mp4.txt" })
	void nonVideoFilesAreIgnored(String name) {
		assertThat(MediaFileClassifier.classify(name)).isEqualTo(MediaFileKind.IGNORED);
	}

	@ParameterizedTest
	@ValueSource(strings = { "._movie.mp4", ".hidden.mkv", ".mp4" })
	void hiddenFilesAreIgnoredEvenWithAVideoExtension(String name) {
		assertThat(MediaFileClassifier.classify(name)).isEqualTo(MediaFileKind.IGNORED);
	}

	@ParameterizedTest
	@ValueSource(strings = { "mp4", "movie.", "README", "" , "   " })
	void namesWithoutAnExtensionAreIgnored(String name) {
		assertThat(MediaFileClassifier.classify(name)).isEqualTo(MediaFileKind.IGNORED);
	}

	@Test
	void nullNameIsIgnored() {
		assertThat(MediaFileClassifier.classify(null)).isEqualTo(MediaFileKind.IGNORED);
	}

}
