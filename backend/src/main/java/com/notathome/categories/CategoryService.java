package com.notathome.categories;

import com.notathome.posts.PostRepository;
import com.notathome.topics.TopicRepository;
import com.notathome.uploads.Upload;
import com.notathome.uploads.UploadRepository;
import com.notathome.uploads.UploadUrl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

  private final CategoryRepository categories;
  private final UploadRepository uploads;
  private final TopicRepository topics;
  private final PostRepository posts;

  CategoryService(
      CategoryRepository categories,
      UploadRepository uploads,
      TopicRepository topics,
      PostRepository posts) {
    this.categories = categories;
    this.uploads = uploads;
    this.topics = topics;
    this.posts = posts;
  }

  /** FR-2.2: every active category, in display order. */
  public List<CategoryResponse> list() {
    return withStats(categories.findAllByDeletedAtIsNullOrderByPositionAsc());
  }

  public Optional<CategoryResponse> getBySlug(String slug) {
    return categories
        .findBySlugAndDeletedAtIsNull(slug)
        .map(category -> withStats(List.of(category)).getFirst());
  }

  /** The public responses: covers resolved, and counts and latest activity filled in (FR-2.2). */
  private List<CategoryResponse> withStats(List<Category> found) {
    List<Long> ids = found.stream().map(Category::getId).toList();
    Map<Long, String> coverUrls = coverUrls(found);
    Map<Long, TopicRepository.CategoryTopicStats> topicStats = new HashMap<>();
    topics.statsByCategory(ids).forEach(stats -> topicStats.put(stats.categoryId(), stats));
    Map<Long, Long> postCounts = new HashMap<>();
    posts.countByCategory(ids).forEach(count -> postCounts.put(count.categoryId(), count.posts()));
    return found.stream()
        .map(
            category -> {
              TopicRepository.CategoryTopicStats stats = topicStats.get(category.getId());
              return CategoryResponse.from(
                  category,
                  coverUrls.get(category.getCoverUploadId()),
                  stats == null ? 0 : stats.topics(),
                  postCounts.getOrDefault(category.getId(), 0L),
                  stats == null ? null : stats.latestActivityAt());
            })
        .toList();
  }

  /** Also used by the admin endpoints, so cover URL resolution lives in one place. */
  CategoryResponse toResponse(Category category) {
    return CategoryResponse.from(category, coverUrl(category));
  }

  private String coverUrl(Category category) {
    if (category.getCoverUploadId() == null) {
      return null;
    }
    return uploads.findById(category.getCoverUploadId()).map(UploadUrl::of).orElse(null);
  }

  private Map<Long, String> coverUrls(List<Category> found) {
    List<Long> ids =
        found.stream().map(Category::getCoverUploadId).filter(id -> id != null).toList();
    return uploads.findAllById(ids).stream()
        .collect(Collectors.toMap(Upload::getId, UploadUrl::of));
  }
}
