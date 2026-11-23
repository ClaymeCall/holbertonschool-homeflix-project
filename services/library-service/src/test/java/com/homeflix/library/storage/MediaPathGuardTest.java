package com.homeflix.library.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.homeflix.library.storage.MediaAccessDeniedException.Reason;

class MediaPathGuardTest {

	@TempDir
	Path base;

	Path root;
	Path outside;
	MediaPathGuard guard;

	@BeforeEach
	void setUp() throws IOException {
		root = Files.createDirectories(base.resolve("library"));
		outside = Files.createDirectories(base.resolve("secret"));
		Files.writeString(root.resolve("movie.mp4"), "video");
		Files.writeString(outside.resolve("passwd"), "secret");
		guard = new MediaPathGuard(List.of(root));
	}

	@Test
	void fileInsideTheRootIsAllowed() throws IOException {
		assertThat(guard.resolve(root.resolve("movie.mp4"))).isEqualTo(root.toRealPath().resolve("movie.mp4"));
	}

	@Test
	void parentSequencesThatLeaveTheRootAreRefused() {
		Path sneaky = root.resolve("../secret/passwd");
		assertDenied(sneaky, Reason.OUTSIDE_ROOTS);
	}

	@Test
	void parentSequencesThatStayInsideTheRootAreAllowed() throws IOException {
		Files.createDirectories(root.resolve("sub"));
		assertThat(guard.resolve(root.resolve("sub/../movie.mp4")).getFileName().toString()).isEqualTo("movie.mp4");
	}

	@Test
	void absolutePathOutsideTheRootIsRefused() {
		assertDenied(outside.resolve("passwd"), Reason.OUTSIDE_ROOTS);
	}

	@Test
	void symbolicLinkPointingOutsideTheRootIsRefused() throws IOException {
		Path link = Files.createSymbolicLink(root.resolve("escape.mp4"), outside.resolve("passwd"));
		assertDenied(link, Reason.OUTSIDE_ROOTS);
	}

	@Test
	void symbolicLinkedDirectoryPointingOutsideTheRootIsRefused() throws IOException {
		Files.createSymbolicLink(root.resolve("dir"), outside);
		assertDenied(root.resolve("dir/passwd"), Reason.OUTSIDE_ROOTS);
	}

	@Test
	void symbolicLinkPointingInsideTheRootIsAllowed() throws IOException {
		Path link = Files.createSymbolicLink(root.resolve("alias.mp4"), root.resolve("movie.mp4"));
		assertThat(guard.resolve(link).getFileName().toString()).isEqualTo("movie.mp4");
	}

	@Test
	void siblingDirectoryWithTheRootNameAsPrefixIsRefused() throws IOException {
		Path evil = Files.createDirectories(base.resolve("library-evil"));
		Files.writeString(evil.resolve("movie.mp4"), "evil");
		assertDenied(evil.resolve("movie.mp4"), Reason.OUTSIDE_ROOTS);
	}

	@Test
	void symbolicLinkedRootStillWorks() throws IOException {
		Path linkedRoot = Files.createSymbolicLink(base.resolve("linked-root"), root);
		MediaPathGuard linked = new MediaPathGuard(List.of(linkedRoot));
		assertThat(linked.resolve(root.resolve("movie.mp4")).getFileName().toString()).isEqualTo("movie.mp4");
	}

	@Test
	void missingFileIsReportedAsNotFound() {
		assertDenied(root.resolve("nope.mp4"), Reason.NOT_FOUND);
	}

	@Test
	void anyOfSeveralRootsIsAccepted() throws IOException {
		Path downloads = Files.createDirectories(base.resolve("downloads"));
		Files.writeString(downloads.resolve("new.mp4"), "video");
		MediaPathGuard two = new MediaPathGuard(List.of(root, downloads));
		assertThat(two.resolve(downloads.resolve("new.mp4")).getFileName().toString()).isEqualTo("new.mp4");
	}

	@Test
	void inaccessibleRootAuthorizesNothing() {
		MediaPathGuard none = new MediaPathGuard(List.of(base.resolve("missing-root")));
		assertDenied(root.resolve("movie.mp4"), none, Reason.OUTSIDE_ROOTS);
	}

	@Test
	void aFolderThatCannotBeSearchedIsUnreadableNotMissing() throws IOException {
		Path locked = Files.createDirectory(root.resolve("locked"));
		Files.writeString(locked.resolve("movie.mp4"), "video");
		Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("---------"));
		try {
			org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(locked.resolve("movie.mp4")),
					"running as a user that ignores permissions");
			assertDenied(locked.resolve("movie.mp4"), Reason.UNREADABLE);
		}
		finally {
			Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
		}
	}

	@Test
	void userMessageNeverContainsAPath() {
		assertThatThrownBy(() -> guard.resolve(outside.resolve("passwd")))
				.isInstanceOf(MediaAccessDeniedException.class)
				.hasMessage("This file cannot be accessed.")
				.satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret"));
	}

	private void assertDenied(Path path, Reason reason) {
		assertDenied(path, guard, reason);
	}

	private void assertDenied(Path path, MediaPathGuard g, Reason reason) {
		assertThatThrownBy(() -> g.resolve(path))
				.isInstanceOfSatisfying(MediaAccessDeniedException.class, e -> assertThat(e.reason()).isEqualTo(reason));
	}

}
