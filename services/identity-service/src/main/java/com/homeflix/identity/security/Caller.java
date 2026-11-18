package com.homeflix.identity.security;

import java.util.UUID;

/**
 * Who is making the request, as stated by the signed token (docs/architecture/identity-and-permissions.md).
 * {@code profileId} is null until the user has chosen a profile in this session.
 * Same record as in the library and acquisition services.
 */
public record Caller(UUID accountId, boolean admin, UUID profileId) {

}
