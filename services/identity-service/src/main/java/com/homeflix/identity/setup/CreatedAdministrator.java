package com.homeflix.identity.setup;

import java.util.UUID;

/**
 * Account created by the first-run setup. Never contains the password or its hash.
 */
public record CreatedAdministrator(UUID id, String email, boolean admin) {
}
