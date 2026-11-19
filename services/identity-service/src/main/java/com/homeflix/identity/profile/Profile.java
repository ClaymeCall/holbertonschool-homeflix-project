package com.homeflix.identity.profile;

import java.time.Instant;
import java.util.UUID;

/** The {@code Profile} of docs/api/identity.yaml. */
public record Profile(UUID id, String name, Instant createdAt) {
}
