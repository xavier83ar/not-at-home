package com.notathome.posts;

import com.notathome.auth.AuthUser;
import com.notathome.auth.RequiresVerifiedEmail;
import com.notathome.common.PageResponse;
import com.notathome.ratelimit.RateLimitPolicy;
import com.notathome.ratelimit.RateLimited;
import com.notathome.topics.Topic;
import com.notathome.topics.TopicRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class PostController {

  private final PostRepository posts;
  private final TopicRepository topics;
  private final PostService postService;

  PostController(PostRepository posts, TopicRepository topics, PostService postService) {
    this.posts = posts;
    this.topics = topics;
    this.postService = postService;
  }

  /** FR-5.1: the author edits their own post. */
  @PutMapping("/api/posts/{id}")
  @RequiresVerifiedEmail
  @RateLimited(RateLimitPolicy.POSTING)
  PostResponse edit(
      @PathVariable Long id,
      @Valid @RequestBody PostContentRequest request,
      @AuthenticationPrincipal AuthUser author) {
    return postService.edit(id, author.getId(), request.content());
  }

  /** FR-5.2 and FR-5.3: the author deletes their own post. */
  @DeleteMapping("/api/posts/{id}")
  @RequiresVerifiedEmail
  DeletePostResponse delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser author) {
    return postService.delete(id, author.getId());
  }

  /**
   * FR-4.6: public. Posts are numbered by their position in the topic, removed ones included (they
   * stay as placeholders), so a post's page never changes when others are deleted.
   */
  @GetMapping("/api/posts/{id}/location")
  PostLocation location(@PathVariable Long id) {
    Post post =
        posts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    // The topic may be removed too: its page is where the "removed" notice is shown (FR-5.7).
    Topic topic = topics.findById(post.getTopicId()).orElseThrow();
    long position = posts.countByTopicIdAndIdLessThan(topic.getId(), post.getId());
    int page = (int) (position / PageResponse.PAGE_SIZE) + 1;
    return new PostLocation(post.getId(), topic.getId(), topic.getSlug(), page);
  }
}
