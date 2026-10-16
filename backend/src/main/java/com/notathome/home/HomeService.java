package com.notathome.home;

import com.notathome.categories.CategoryService;
import com.notathome.posts.PostRepository;
import com.notathome.topics.TopicService;
import com.notathome.users.AuthorResponse;
import com.notathome.users.Authors;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class HomeService {

  static final int HIGHLIGHTED_CATEGORIES = 4;
  static final int LATEST_TOPICS = 5;
  static final int ACTIVE_USERS = 5;

  /** "Recent" for the highlighted categories and the most active users. */
  static final Duration PERIOD = Duration.ofDays(30);

  private final CategoryService categories;
  private final TopicService topics;
  private final PostRepository posts;
  private final Authors authors;

  HomeService(
      CategoryService categories, TopicService topics, PostRepository posts, Authors authors) {
    this.categories = categories;
    this.topics = topics;
    this.posts = posts;
    this.authors = authors;
  }

  @Transactional(readOnly = true)
  HomeResponse home() {
    Instant since = Instant.now().minus(PERIOD);
    return new HomeResponse(
        categories.highlighted(HIGHLIGHTED_CATEGORIES, since),
        topics.latest(LATEST_TOPICS),
        activeUsers(since));
  }

  private List<ActiveUserResponse> activeUsers(Instant since) {
    List<PostRepository.AuthorPostCount> counts =
        posts.mostActiveAuthorsSince(since, PageRequest.of(0, ACTIVE_USERS));
    Map<Long, AuthorResponse> byId =
        authors.byId(counts.stream().map(PostRepository.AuthorPostCount::authorId).toList());
    return counts.stream()
        .map(count -> new ActiveUserResponse(byId.get(count.authorId()), count.posts()))
        .toList();
  }
}
