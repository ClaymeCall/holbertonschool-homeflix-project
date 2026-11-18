package com.homeflix.library.roots;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /roots}. The rest of the checks need the file system, see {@link RootValidator}. */
public record RootRequest(
		@NotBlank(message = "Enter the path of a folder.")
		@Size(max = 4096, message = "The path is too long.")
		String path) {

}
