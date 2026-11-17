package com.homeflix.identity.session;

import java.util.UUID;

/**
 * What a session token says (docs/api/README.md, "Session and token"):
 * the account ({@code sub}), the administrator flag ({@code admin}) and the active
 * profile ({@code profileId}, null until a profile is selected).
 */
public record SessionClaims(UUID accountId, boolean admin, UUID profileId) {
}
