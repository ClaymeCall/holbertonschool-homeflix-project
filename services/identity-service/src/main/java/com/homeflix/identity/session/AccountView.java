package com.homeflix.identity.session;

import java.util.UUID;

/**
 * The {@code Account} of docs/api/identity.yaml: the signed-in account and its active profile
 * (null until one is selected). Never contains the password or its hash.
 */
public record AccountView(UUID id, String email, boolean admin, UUID profileId) {
}
