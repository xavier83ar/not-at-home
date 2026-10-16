package com.notathome.home;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.users.AuthorResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** One of the most active users, with how many posts they wrote in the period. */
public record ActiveUserResponse(
    @Schema(requiredMode = REQUIRED) AuthorResponse user,
    @Schema(requiredMode = REQUIRED) long postCount) {}
