package com.homeflix.identity.setup;

/**
 * Tells the interface whether the first-run setup screen must be shown (G6).
 */
public record SetupStatus(boolean setupRequired) {
}