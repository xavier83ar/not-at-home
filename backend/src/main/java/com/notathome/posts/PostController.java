package com.notathome.posts;

import com.notathome.common.PageResponse;
import com.notathome.topics.Topic;
import com.notathome.topics.TopicRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class PostController {

  private final PostRepository posts;
  private final TopicRepository topics;

  PostController(PostRepository posts, TopicRepository topics) {
    this.posts = posts;
    this.topics = topics;
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
