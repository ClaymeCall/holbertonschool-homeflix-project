package com.homeflix.library.roots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.homeflix.library.web.RejectedRequestException;

/** The checks of LIB-03 and R-02, against a real temporary file system. */
class RootValidatorTest {

	@TempDir
	Path temp;

	private Path library;
	private Path downloads;
	private RootValidator validator;

	@BeforeEach
	void setUp() throws IOException {
		library = Files.createDirectory(temp.resolve("library"));
		downloads = Files.createDirectory(temp.resolve("downloads"));
		validator = new RootValidator(List.of(library.toString(), downloads.toString()));
	}

	@Test
	void acceptsAFolderInsideAMountedPath() throws IOException {
		Path films = Files.createDirectory(library.resolve("films"));
		RootValidator.Accepted accepted = validator.validate(films.toString());
		assertThat(accepted.path()).isEqualTo(films.toString());
		assertThat(accepted.realPath()).isEqualTo(films.toRealPath());
	}

	@Test
	void acceptsTheMountedPathItself() {
		assertThat(validator.validate(library.toString()).path()).isEqualTo(library.toString());
	}

	@Test
	void storesTheNormalisedPath() throws IOException {
		Path films = Files.createDirectory(library.resolve("films"));
		assertThat(validator.validate(films + "/").path()).isEqualTo(films.toString());
		assertThat(validator.validate("  " + films + "  ").path()).isEqualTo(films.toString());
		assertThat(validator.validate(library + "/films/../films").path()).isEqualTo(films.toString());
	}

	@Test
	void refusesAFolderOutsideTheMountedPaths() throws IOException {
		Path elsewhere = Files.createDirectory(temp.resolve("elsewhere"));
		assertCode(elsewhere.toString(), "directory_outside_library");
	}

	@Test
	void refusesASiblingThatOnlySharesATextPrefix() throws IOException {
		Path evil = Files.createDirectory(temp.resolve("library-evil"));
		assertCode(evil.toString(), "directory_outside_library");
	}

	@Test
	void refusesADotDotThatLeavesTheMountedPath() {
		assertCode(library + "/../..", "directory_outside_library");
	}

	@Test
	void doesNotSayWhetherAPathOutsideTheMountedPathsExists() {
		// Same answer for a path that exists and one that does not, so existence is not revealed.
		assertCode("/etc", "directory_outside_library");
		assertCode("/no/such/place", "directory_outside_library");
	}

	@Test
	void refusesAFolderThatDoesNotExist() {
		assertCode(library.resolve("missing").toString(), "directory_not_found");
	}

	@Test
	void refusesAFile() throws IOException {
		Path file = Files.createFile(library.resolve("movie.mp4"));
		assertCode(file.toString(), "not_a_directory");
	}

	@Test
	void refusesAFolderThatCannotBeRead() throws IOException {
		Path locked = Files.createDirectory(library.resolve("locked"));
		Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
		try {
			assumeFalse(Files.isReadable(locked), "running as a user that can read anything");
			assertCode(locked.toString(), "directory_not_readable");
		}
		finally {
			Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
		}
	}

	@Test
	void refusesALinkThatLeadsOutsideTheMountedPaths() throws IOException {
		Path elsewhere = Files.createDirectory(temp.resolve("elsewhere"));
		Path link = Files.createSymbolicLink(library.resolve("escape"), elsewhere);
		assertCode(link.toString(), "directory_outside_library");
	}

	@Test
	void acceptsALinkThatStaysInsideTheMountedPaths() throws IOException {
		Path films = Files.createDirectory(library.resolve("films"));
		Path link = Files.createSymbolicLink(library.resolve("alias"), films);
		assertThat(validator.validate(link.toString()).realPath()).isEqualTo(films.toRealPath());
	}

	@Test
	void refusesARelativePathWithAFieldMessage() {
		assertThatThrownBy(() -> validator.validate("films"))
				.isInstanceOfSatisfying(RejectedRequestException.class, e -> {
					assertThat(e.code()).isEqualTo("validation_failed");
					assertThat(e.fields()).containsKey("path");
				});
	}

	@Test
	void refusesAPathThatIsNotAValidPath() {
		assertThatThrownBy(() -> validator.validate("/media/lib\0rary"))
				.isInstanceOfSatisfying(RejectedRequestException.class,
						e -> assertThat(e.code()).isEqualTo("validation_failed"));
	}

	@Test
	void messagesNeverContainAPath() throws IOException {
		Path file = Files.createFile(library.resolve("movie.mp4"));
		for (String requested : List.of(file.toString(), library.resolve("missing").toString(), "/etc")) {
			assertThatThrownBy(() -> validator.validate(requested))
					.isInstanceOfSatisfying(RejectedRequestException.class, e -> {
						assertThat(e.getMessage()).doesNotContain("/");
						assertThat(e.getMessage()).doesNotContain(temp.toString());
					});
		}
	}

	private void assertCode(String requested, String code) {
		assertThatThrownBy(() -> validator.validate(requested))
				.isInstanceOfSatisfying(RejectedRequestException.class,
						e -> assertThat(e.code()).isEqualTo(code));
	}

}
