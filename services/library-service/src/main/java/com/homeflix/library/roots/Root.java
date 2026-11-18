package com.homeflix.library.roots;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An authorized media folder (LIB-01). */
public record Root(UUID id, String path, OffsetDateTime createdAt) {

}
