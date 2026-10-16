package com.notathome.topics;

import com.notathome.auth.AuthUser;
import com.notathome.auth.RequiresVerifiedEmail;
import com.notathome.common.PageResponse;
import com.notathome.posts.PostContentRequest;
import com.notathome.posts.PostResponse;
import com.notathome.ratelimit.RateLimitPolicy;
import com.notathome.ratelimit.RateLimited;
import com.notathome.users.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class TopicController {

  private final TopicService topics;

  TopicController(TopicService topics) {
    this.topics = topics;
  }

  /** FR-3.2: public. {@code page} is 1-based (FR-7.1). */
  @GetMapping("/api/categories/{slug}/topics")
  PageResponse<TopicSummary> list(
      @PathVariable String slug, @RequestParam(defaultValue = "1") int page) {
    return topics
        .list(slug, page)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  /** Public: the topics of every category, most recently active first. */
  @GetMapping("/api/topics")
  PageResponse<TopicSummary> listAll(@RequestParam(defaultValue = "1") int page) {
    return topics.listAll(page);
  }

  /** FR-4.3: public. The viewer is only needed to know whether they are an admin (FR-5.8). */
  @GetMapping("/api/topics/{id}")
  TopicPageResponse get(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") int page,
      @AuthenticationPrincipal AuthUser viewer) {
    return topics.getPage(id, page, isAdmin(viewer));
  }

  @PostMapping("/api/topics")
  @RequiresVerifiedEmail
  @RateLimited(RateLimitPolicy.POSTING)
  @ResponseStatus(HttpStatus.CREATED)
  TopicSummary create(
      @Valid @RequestBody CreateTopicRequest request, @AuthenticationPrincipal AuthUser author) {
    return topics.create(author.getId(), request);
  }

  @PostMapping("/api/topics/{id}/posts")
  @RequiresVerifiedEmail
  @RateLimited(RateLimitPolicy.POSTING)
  @ResponseStatus(HttpStatus.CREATED)
  PostResponse reply(
      @PathVariable Long id,
      @Valid @RequestBody PostContentRequest request,
      @AuthenticationPrincipal AuthUser author) {
    return topics.reply(id, author.getId(), request.content());
  }

  private static boolean isAdmin(AuthUser viewer) {
    return viewer != null && viewer.getRole() == UserRole.ADMIN;
  }
}
