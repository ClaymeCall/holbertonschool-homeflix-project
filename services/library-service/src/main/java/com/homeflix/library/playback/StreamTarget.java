package com.homeflix.library.playback;

/**
 * Where the file of a title is, as the library's own database records it. The client never sends a
 * path (docs/library/file-access.md). {@code rootPath} and {@code relativePath} are null when the
 * title has no file.
 */
record StreamTarget(String availability, String rootPath, String relativePath) {

}
