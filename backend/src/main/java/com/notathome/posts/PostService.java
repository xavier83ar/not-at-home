package com.notathome.posts;

import com.notathome.content.EditorContentProcessor;
import com.notathome.topics.Topic;
import com.notathome.topics.TopicCounters;
import com.notathome.topics.TopicRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** Editing and deleting one's own posts (FR-5.1 to FR-5.3). Admins' versions come with M8. */
@Service
class PostService {

  private final PostRepository posts;
  private final TopicRepository topics;
  private final TopicCounters counters;
  private final EditorContentProcessor contentProcessor;
  private final PostResponses postResponses;

  PostService(
      PostRepository posts,
      TopicRepository topics,
      TopicCounters counters,
      EditorContentProcessor contentProcessor,
      PostResponses postResponses) {
    this.posts = posts;
    this.topics = topics;
    this.counters = counters;
    this.contentProcessor = contentProcessor;
    this.postResponses = postResponses;
  }

  /** FR-5.1: any time, but only the author, and only while the post is not removed. */
  @Transactional
  PostResponse edit(Long postId, Long userId, JsonNode document) {
    Topic topic = lockTopicOf(postId);
    Post post = ownPost(postId, userId);
    post.edit(contentProcessor.process(document), Instant.now());
    return postResponses.toResponses(List.of(post), openingPostId(topic), false).getFirst();
  }

  /**
   * FR-5.2 and FR-5.3. The opening post of a topic nobody has replied to takes the topic with it;
   * with replies the topic stays and the post shows as removed. Counters are recounted either way.
   */
  @Transactional
  DeletePostResponse delete(Long postId, Long userId) {
    Topic topic = lockTopicOf(postId);
    Post post = ownPost(postId, userId);
    Instant now = Instant.now();
    post.remove(now);
    counters.refresh(topic);

    boolean opening = post.getId().equals(openingPostId(topic));
    if (opening && topic.getReplyCount() == 0) {
      posts.removeAllOfTopic(topic.getId(), now);
      topic.remove(now);
      return new DeletePostResponse(true);
    }
    return new DeletePostResponse(false);
  }

  /**
   * Takes the row lock of the post's topic (see {@link TopicRepository#findActiveForUpdate}) before
   * the post itself is read, so what is read afterwards is what the topic's counters are built
   * from. A post in a removed topic counts as gone.
   */
  private Topic lockTopicOf(Long postId) {
    return posts
        .findTopicId(postId)
        .flatMap(topics::findActiveForUpdate)
        .orElseThrow(PostService::notFound);
  }

  private Post ownPost(Long postId, Long userId) {
    Post post =
        posts.findById(postId).filter(p -> !p.isDeleted()).orElseThrow(PostService::notFound);
    if (!post.getAuthorId().equals(userId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You can only change your own posts.");
    }
    return post;
  }

  private Long openingPostId(Topic topic) {
    return posts.findFirstByTopicIdOrderByIdAsc(topic.getId()).orElseThrow().getId();
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
