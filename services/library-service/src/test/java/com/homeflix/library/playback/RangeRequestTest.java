package com.homeflix.library.playback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.homeflix.library.playback.RangeRequest.Partial;
import com.homeflix.library.playback.RangeRequest.Unsatisfiable;
import com.homeflix.library.playback.RangeRequest.Whole;

/** The Range header rules of PLY-03, on a file of 1000 bytes (positions 0 to 999). */
class RangeRequestTest {

	private static final long SIZE = 1000;

	private static RangeRequest parse(String header) {
		return RangeRequest.parse(header, SIZE);
	}

	@Test
	void noHeaderMeansTheWholeFile() {
		assertThat(parse(null)).isInstanceOf(Whole.class);
		assertThat(parse("")).isInstanceOf(Whole.class);
		assertThat(parse("   ")).isInstanceOf(Whole.class);
	}

	@Test
	void aClosedRangeIsServed() {
		assertThat(parse("bytes=0-99")).isEqualTo(new Partial(0, 99));
		assertThat(parse("bytes=100-199")).isEqualTo(new Partial(100, 199));
		assertThat(((Partial) parse("bytes=0-99")).length()).isEqualTo(100);
	}

	@Test
	void aSingleByteIsServed() {
		assertThat(parse("bytes=5-5")).isEqualTo(new Partial(5, 5));
		assertThat(parse("bytes=999-999")).isEqualTo(new Partial(999, 999));
	}

	@Test
	void anOpenEndedRangeGoesToTheEnd() {
		assertThat(parse("bytes=500-")).isEqualTo(new Partial(500, 999));
		assertThat(parse("bytes=0-")).isEqualTo(new Partial(0, 999));
		assertThat(parse("bytes=999-")).isEqualTo(new Partial(999, 999));
	}

	@Test
	void aSuffixRangeIsTheLastBytes() {
		assertThat(parse("bytes=-100")).isEqualTo(new Partial(900, 999));
		assertThat(parse("bytes=-1")).isEqualTo(new Partial(999, 999));
	}

	@Test
	void aSuffixLongerThanTheFileIsTheWholeFileAsAPartialAnswer() {
		assertThat(parse("bytes=-5000")).isEqualTo(new Partial(0, 999));
	}

	@Test
	void anEndBeyondTheFileIsCutToTheLastByte() {
		assertThat(parse("bytes=900-5000")).isEqualTo(new Partial(900, 999));
		assertThat(parse("bytes=0-1000")).isEqualTo(new Partial(0, 999));
	}

	@Test
	void aStartAtOrAfterTheEndCannotBeSatisfied() {
		assertThat(parse("bytes=1000-")).isInstanceOf(Unsatisfiable.class);
		assertThat(parse("bytes=1000-1100")).isInstanceOf(Unsatisfiable.class);
		assertThat(parse("bytes=5000-")).isInstanceOf(Unsatisfiable.class);
	}

	@Test
	void aSuffixOfZeroBytesCannotBeSatisfied() {
		assertThat(parse("bytes=-0")).isInstanceOf(Unsatisfiable.class);
	}

	@Test
	void severalRangesAreRefused() {
		assertThat(parse("bytes=0-99,200-299")).isInstanceOf(Unsatisfiable.class);
		assertThat(parse("bytes=0-1, 5-6")).isInstanceOf(Unsatisfiable.class);
	}

	@Test
	void aHeaderThatIsNotAWellFormedByteRangeIsIgnored() {
		assertThat(parse("items=0-5")).isInstanceOf(Whole.class);
		assertThat(parse("bytes=")).isInstanceOf(Whole.class);
		assertThat(parse("bytes=-")).isInstanceOf(Whole.class);
		assertThat(parse("bytes=abc-def")).isInstanceOf(Whole.class);
		assertThat(parse("bytes=5")).isInstanceOf(Whole.class);
		assertThat(parse("bytes=-5-")).isInstanceOf(Whole.class);
		assertThat(parse("bytes=1.5-3")).isInstanceOf(Whole.class);
		assertThat(parse("garbage")).isInstanceOf(Whole.class);
	}

	@Test
	void anEndBeforeTheStartIsIgnored() {
		assertThat(parse("bytes=500-100")).isInstanceOf(Whole.class);
	}

	@Test
	void theUnitIsNotCaseSensitive() {
		assertThat(parse("Bytes=0-9")).isEqualTo(new Partial(0, 9));
		assertThat(parse("BYTES=0-9")).isEqualTo(new Partial(0, 9));
	}

	@Test
	void spacesAroundTheHeaderAreTolerated() {
		assertThat(parse("  bytes=0-9  ")).isEqualTo(new Partial(0, 9));
	}

	@Test
	void numbersTooLargeForALongDoNotCrash() {
		assertThat(parse("bytes=99999999999999999999-")).isInstanceOf(Unsatisfiable.class);
		assertThat(parse("bytes=0-99999999999999999999")).isEqualTo(new Partial(0, 999));
		assertThat(parse("bytes=-99999999999999999999")).isEqualTo(new Partial(0, 999));
	}

	@Test
	void anEmptyFileCannotSatisfyAnyRange() {
		assertThat(RangeRequest.parse("bytes=0-", 0)).isInstanceOf(Unsatisfiable.class);
		assertThat(RangeRequest.parse("bytes=-5", 0)).isInstanceOf(Unsatisfiable.class);
		assertThat(RangeRequest.parse(null, 0)).isInstanceOf(Whole.class);
	}

}
