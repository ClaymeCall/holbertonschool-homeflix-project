package com.homeflix.identity.account;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /accounts/{accountId}/role}: true for administrator, false for user. */
public record RoleRequest(

		@NotNull(message = "Choose administrator or user.")
		Boolean admin) {
}
