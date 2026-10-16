package com.notathome.topics;

import com.notathome.posts.Post;
import com.notathome.posts.PostRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Works out a topic's {@code reply_count} and {@code last_post_at} from its posts. They are always
 * recounted, never adjusted by one, so every kind of change (reply, delete...) goes through the
 * same code and they cannot drift. Callers hold the topic's row lock (see {@link
 * TopicRepository#findActiveForUpdate}) and call this in the same transaction as the change.
 */
@Component
public class TopicCounters {

  private final PostRepository posts;

  TopicCounters(PostRepository posts) {
    this.posts = posts;
  }

  /** Replies are the visible posts other than the opening one (FR-5.5). */
  public void refresh(Topic topic) {
    Post opening = posts.findFirstByTopicIdOrderByIdAsc(topic.getId()).orElseThrow();
    int replies =
        (int) posts.countByTopicIdAndDeletedAtIsNullAndIdNot(topic.getId(), opening.getId());
    Instant lastPostAt = posts.latestVisibleAt(topic.getId()).orElse(topic.getCreatedAt());
    topic.updateActivity(replies, lastPostAt);
  }
}
