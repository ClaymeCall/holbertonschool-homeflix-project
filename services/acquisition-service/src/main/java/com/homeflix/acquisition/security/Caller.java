package com.homeflix.acquisition.security;

import java.util.UUID;

/**
 * Who is making the request, as stated by the signed token (docs/architecture/identity-and-permissions.md).
 * {@code profileId} is null until the user has chosen a profile in this session.
 */
public record Caller(UUID accountId, boolean admin, UUID profileId) {

}
