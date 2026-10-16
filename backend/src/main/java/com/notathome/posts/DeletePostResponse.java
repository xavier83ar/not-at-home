package com.notathome.posts;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param topicRemoved true when deleting the opening post of a topic without replies took the whole
 *     topic with it (FR-5.3), so the page showing it has nothing left to show
 */
public record DeletePostResponse(@Schema(requiredMode = REQUIRED) boolean topicRemoved) {}
