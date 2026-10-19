package com.notathome.topics;

import com.notathome.categories.Category;
import com.notathome.categories.CategoryRepository;
import com.notathome.common.FieldErrorsException;
import com.notathome.common.PageResponse;
import com.notathome.common.Slug;
import com.notathome.content.EditorContentProcessor;
import com.notathome.content.ProcessedContent;
import com.notathome.posts.Post;
import com.notathome.posts.PostRepository;
import com.notathome.posts.PostResponse;
import com.notathome.posts.PostResponses;
import com.notathome.users.AuthorResponse;
import com.notathome.users.Authors;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
public class TopicService {

  private final TopicRepository topics;
  private final PostRepository posts;
  private final CategoryRepository categories;
  private final EditorContentProcessor contentProcessor;
  private final TopicCounters counters;
  private final Authors authors;
  private final PostResponses postResponses;

  TopicService(
      TopicRepository topics,
      PostRepository posts,
      CategoryRepository categories,
      EditorContentProcessor contentProcessor,
      TopicCounters counters,
      Authors authors,
      PostResponses postResponses) {
    this.topics = topics;
    this.posts = posts;
    this.categories = categories;
    this.contentProcessor = contentProcessor;
    this.counters = counters;
    this.authors = authors;
    this.postResponses = postResponses;
  }

  /** FR-3.1: the topic and its opening post are saved together or not at all. */
  @Transactional
  TopicSummary create(Long authorId, CreateTopicRequest request) {
    return create(authorId, request.categoryId(), request.title(), request.content());
  }

  /** For the seed command, which has no HTTP request to build a {@link CreateTopicRequest} from. */
  @Transactional
  public TopicSummary create(Long authorId, Long categoryId, String rawTitle, JsonNode document) {
    Category category =
        categories
            .findByIdAndDeletedAtIsNull(categoryId)
            .orElseThrow(
                () ->
                    new FieldErrorsException(Map.of("categoryId", List.of("category not found"))));
    ProcessedContent content = contentProcessor.process(document);

    String title = rawTitle.strip();
    Instant now = Instant.now();
    Topic topic =
        topics.save(new Topic(category.getId(), authorId, title, Slug.of(title, "topic"), now));
    posts.save(new Post(topic.getId(), authorId, content, now));
    counters.refresh(topic);
    return summaries(List.of(topic)).getFirst();
  }

  /** FR-4.1. Refused on a locked topic, whoever asks (FR-3.4). */
  @Transactional
  public PostResponse reply(Long topicId, Long authorId, JsonNode document) {
    Topic topic = topics.findActiveForUpdate(topicId).orElseThrow(TopicService::notFound);
    if (topic.isLocked()) {
      throw new TopicLockedException();
    }
    ProcessedContent content = contentProcessor.process(document);
    Post post = posts.save(new Post(topicId, authorId, content, Instant.now()));
    counters.refresh(topic);
    return postResponses.toResponses(List.of(post), openingPostId(topicId), false).getFirst();
  }

  /** FR-3.2, FR-3.3, FR-7.1: a category's topics. Empty when the category has none. */
  @Transactional(readOnly = true)
  Optional<PageResponse<TopicSummary>> list(String categorySlug, int page) {
    return categories
        .findBySlugAndDeletedAtIsNull(categorySlug)
        .map(
            category ->
                PageResponse.of(
                    topics.findByCategoryIdAndDeletedAtIsNullOrderByPinnedDescLastPostAtDescIdDesc(
                        category.getId(), PageResponse.request(page, Sort.unsorted())),
                    this::summaries));
  }

  /** Every category's topics together, most recently active first. */
  @Transactional(readOnly = true)
  PageResponse<TopicSummary> listAll(int page) {
    return PageResponse.of(
        topics.findByDeletedAtIsNullOrderByLastPostAtDescIdDesc(
            PageResponse.request(page, Sort.unsorted())),
        this::summaries);
  }

  /**
   * The home page's latest topics (section 8.2): the most recently active, whatever the category.
   */
  @Transactional(readOnly = true)
  public List<TopicSummary> latest(int limit) {
    return summaries(
        topics
            .findByDeletedAtIsNullOrderByLastPostAtDescIdDesc(PageRequest.of(0, limit))
            .getContent());
  }

  /** The topics an author started most recently, for their profile (FR-8.1). */
  @Transactional(readOnly = true)
  public List<TopicSummary> recentByAuthor(Long authorId, int limit) {
    return summaries(topics.recentByAuthor(authorId, PageRequest.of(0, limit)));
  }

  /**
   * FR-4.3: a topic and one page of its posts. A removed topic answers with {@code removed: true}
   * and the five most recently active topics of its category (FR-5.7); only admins also get what
   * was in it (FR-5.8), so a removed title or post is not public.
   */
  @Transactional(readOnly = true)
  TopicPageResponse getPage(Long id, int page, boolean viewerIsAdmin) {
    Topic topic = topics.findById(id).orElseThrow(TopicService::notFound);
    Category category = categories.findById(topic.getCategoryId()).orElseThrow();
    CategoryRef categoryRef =
        new CategoryRef(category.getId(), category.getName(), category.getSlug());

    List<TopicSummary> recent = List.of();
    if (topic.isDeleted()) {
      recent =
          summaries(
              topics.findTop5ByCategoryIdAndDeletedAtIsNullOrderByLastPostAtDescIdDesc(
                  category.getId()));
      if (!viewerIsAdmin) {
        return new TopicPageResponse(true, categoryRef, null, null, recent);
      }
    }
    Page<Post> found =
        posts.findByTopicIdOrderByIdAsc(id, PageResponse.request(page, Sort.unsorted()));
    Long openingId = openingPostId(id);
    PageResponse<PostResponse> postPage =
        PageResponse.of(found, list -> postResponses.toResponses(list, openingId, viewerIsAdmin));
    return new TopicPageResponse(topic.isDeleted(), categoryRef, detail(topic), postPage, recent);
  }

  private TopicDetail detail(Topic topic) {
    AuthorResponse author = authors.byId(List.of(topic.getAuthorId())).get(topic.getAuthorId());
    return new TopicDetail(
        topic.getId(),
        topic.getSlug(),
        topic.getTitle(),
        author,
        topic.isPinned(),
        topic.isLocked(),
        topic.getReplyCount(),
        topic.getCreatedAt(),
        topic.getLastPostAt());
  }

  private List<TopicSummary> summaries(List<Topic> found) {
    Map<Long, AuthorResponse> byAuthor =
        authors.byId(found.stream().map(Topic::getAuthorId).toList());
    Map<Long, CategoryRef> byCategory = new HashMap<>();
    categories
        .findAllById(found.stream().map(Topic::getCategoryId).distinct().toList())
        .forEach(
            category ->
                byCategory.put(
                    category.getId(),
                    new CategoryRef(category.getId(), category.getName(), category.getSlug())));
    Map<Long, Long> latestPost = new HashMap<>();
    posts
        .latestVisiblePosts(found.stream().map(Topic::getId).toList())
        .forEach(latest -> latestPost.put(latest.topicId(), latest.postId()));
    return found.stream()
        .map(
            topic ->
                new TopicSummary(
                    topic.getId(),
                    topic.getSlug(),
                    topic.getTitle(),
                    byAuthor.get(topic.getAuthorId()),
                    byCategory.get(topic.getCategoryId()),
                    topic.isPinned(),
                    topic.isLocked(),
                    topic.getReplyCount(),
                    topic.getCreatedAt(),
                    topic.getLastPostAt(),
                    latestPost.get(topic.getId())))
        .toList();
  }

  private Long openingPostId(Long topicId) {
    return posts.findFirstByTopicIdOrderByIdAsc(topicId).orElseThrow().getId();
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
