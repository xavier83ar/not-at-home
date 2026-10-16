package com.notathome.content;

/**
 * A post's content after validation (FR-4.4): {@code json} is the canonical Editor.js document,
 * safe to store and to send to the browser, and {@code searchText} its plain text for full-text
 * search.
 */
public record ProcessedContent(String json, String searchText) {}
