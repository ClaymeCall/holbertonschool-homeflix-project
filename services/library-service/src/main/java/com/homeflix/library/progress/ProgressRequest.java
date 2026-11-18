package com.homeflix.library.progress;

/** The body of {@code PUT /progress/{mediaId}}. The fields are objects so a missing one can be named in the error. */
public record ProgressRequest(Integer positionSeconds, Integer durationSeconds) {

}
