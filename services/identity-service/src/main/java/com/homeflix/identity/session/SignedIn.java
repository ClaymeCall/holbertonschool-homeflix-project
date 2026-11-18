package com.homeflix.identity.session;

/**
 * Result of a sign-in or a renewal: the account to return and the token to put in the cookie.
 */
public record SignedIn(AccountView account, String token) {
}
