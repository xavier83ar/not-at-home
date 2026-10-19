package com.notathome.posts;

import com.notathome.topics.TopicCounters;
import com.notathome.topics.TopicRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes everything an author wrote, for when their account is deleted (FR-8.4). */
@Component
public class AuthorPosts {

  private final PostRepository posts;
  private final TopicRepository topics;
  private final TopicCounters counters;

  AuthorPosts(PostRepository posts, TopicRepository topics, TopicCounters counters) {
    this.posts = posts;
    this.topics = topics;
    this.counters = counters;
  }

  /**
   * Deletes all of the author's posts, one topic at a time with the same rules as deleting them by
   * hand (FR-5.2, FR-5.3): a topic they started that nobody replied to goes with its opening post,
   * and one with replies from others stays, with the opening post shown as removed. Each topic is
   * locked and its counters recounted, as in {@code PostService}; topics are taken in id order, so
   * two accounts being deleted at once can't wait on each other. Call inside a transaction.
   */
  @Transactional
  public void removeAll(Long authorId, Instant at) {
    for (Long topicId : posts.activeTopicIdsWithPostsBy(authorId)) {
      topics
          .findActiveForUpdate(topicId)
          .ifPresent(
              topic -> {
                posts.removeByAuthor(topicId, authorId, at);
                counters.refresh(topic);
                Post opening = posts.findFirstByTopicIdOrderByIdAsc(topicId).orElseThrow();
                if (opening.getAuthorId().equals(authorId) && topic.getReplyCount() == 0) {
                  posts.removeAllOfTopic(topicId, at);
                  topic.remove(at);
                }
              });
    }
  }
}
