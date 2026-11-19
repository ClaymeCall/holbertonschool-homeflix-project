package com.homeflix.identity.account;

import java.time.Instant;
import java.util.UUID;

/**
 * The {@code ManagedAccount} of docs/api/identity.yaml: an account as an administrator sees it
 * (ACC-09). Never contains the password or its hash.
 */
public record ManagedAccount(UUID id, String email, boolean admin, boolean disabled, Instant createdAt) {
}
