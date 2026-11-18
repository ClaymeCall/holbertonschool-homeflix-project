package com.homeflix.library.playback;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What to send for the {@code Range} header of a stream request (PLY-03, RFC 9110 section 14).
 *
 * <p>One byte range is accepted, as {@code bytes=START-END}, {@code bytes=START-} or {@code bytes=-SUFFIX}.
 * A header that is not a byte range, or is badly formed, is ignored and the whole file is sent, as the RFC
 * says. A range that starts at or after the end of the file, a suffix of zero bytes, or several ranges
 * at once cannot be satisfied: that is a {@code 416}. An end beyond the file is cut to the last byte.
 */
sealed interface RangeRequest {

	/** Send the whole file with {@code 200}. */
	record Whole() implements RangeRequest {
	}

	/** Send bytes {@code start} to {@code end}, both included, with {@code 206}. */
	record Partial(long start, long end) implements RangeRequest {

		long length() {
			return end - start + 1;
		}

	}

	/** Answer {@code 416}, with a {@code Content-Range} that gives the size of the file. */
	record Unsatisfiable() implements RangeRequest {
	}

	Pattern SINGLE = Pattern.compile("^bytes=(\\d*)-(\\d*)$");

	static RangeRequest parse(String header, long size) {
		if (header == null || header.isBlank()) {
			return new Whole();
		}
		String value = header.trim();
		if (!value.regionMatches(true, 0, "bytes=", 0, "bytes=".length())) {
			return new Whole();
		}
		// Several ranges are refused on purpose: browsers ask for one at a time (docs/api/library.yaml).
		if (value.contains(",")) {
			return new Unsatisfiable();
		}
		Matcher matcher = SINGLE.matcher("bytes=" + value.substring("bytes=".length()).trim());
		if (!matcher.matches() || (matcher.group(1).isEmpty() && matcher.group(2).isEmpty())) {
			return new Whole();
		}
		Long first = number(matcher.group(1));
		Long last = number(matcher.group(2));
		if (first == null) {
			// bytes=-N: the last N bytes.
			if (last == 0 || size == 0) {
				return new Unsatisfiable();
			}
			return new Partial(Math.max(0, size - last), size - 1);
		}
		if (last != null && last < first) {
			return new Whole();
		}
		if (first >= size) {
			return new Unsatisfiable();
		}
		long end = last == null ? size - 1 : Math.min(last, size - 1);
		return new Partial(first, end);
	}

	/** A number too large for a long is a position beyond any file, so it is kept as the largest value. */
	private static Long number(String digits) {
		if (digits.isEmpty()) {
			return null;
		}
		try {
			return Long.parseLong(digits);
		}
		catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

}
