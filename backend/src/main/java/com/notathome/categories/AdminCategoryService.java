package com.notathome.categories;

import com.notathome.common.FieldErrorsException;
import com.notathome.common.Slug;
import com.notathome.topics.TopicRepository;
import com.notathome.uploads.UploadRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCategoryService {

  private final CategoryRepository categories;
  private final UploadRepository uploads;
  private final TopicRepository topics;

  AdminCategoryService(
      CategoryRepository categories, UploadRepository uploads, TopicRepository topics) {
    this.categories = categories;
    this.uploads = uploads;
    this.topics = topics;
  }

  /** For the seed command, which has no HTTP request to build a {@link CategoryRequest} from. */
  @Transactional
  public Category create(String name, String description, Long coverUploadId) {
    return create(new CategoryRequest(name, description, coverUploadId));
  }

  @Transactional
  Category create(CategoryRequest request) {
    validateCoverUpload(request.coverUploadId());
    String slug = uniqueSlug(request.name());
    int position = (int) categories.countByDeletedAtIsNull();
    return categories.save(
        new Category(
            request.name().strip(),
            slug,
            request.description().strip(),
            request.coverUploadId(),
            position));
  }

  @Transactional
  Category update(Long id, CategoryRequest request) {
    Category category =
        categories.findByIdAndDeletedAtIsNull(id).orElseThrow(AdminCategoryService::notFound);
    validateCoverUpload(request.coverUploadId());
    category.rename(request.name().strip());
    category.changeDescription(request.description().strip());
    category.changeCover(request.coverUploadId());
    return category;
  }

  /** The full ordered list of every active category's id; arrow-button reordering, not drag. */
  @Transactional
  void reorder(List<Long> orderedIds) {
    List<Category> current = categories.findAllByDeletedAtIsNullOrderByPositionAsc();
    Set<Long> currentIds = new HashSet<>();
    for (Category category : current) {
      currentIds.add(category.getId());
    }
    if (orderedIds.size() != currentIds.size() || !currentIds.containsAll(orderedIds)) {
      throw new FieldErrorsException(
          Map.of("orderedIds", List.of("must list every active category exactly once")));
    }
    Map<Long, Category> byId = new HashMap<>();
    for (Category category : current) {
      byId.put(category.getId(), category);
    }
    for (int position = 0; position < orderedIds.size(); position++) {
      byId.get(orderedIds.get(position)).reorderTo(position);
    }
  }

  /** Soft delete (FR-2.4): refused with a 409 while the category has active topics. */
  @Transactional
  void delete(Long id) {
    Category category =
        categories.findByIdAndDeletedAtIsNull(id).orElseThrow(AdminCategoryService::notFound);
    if (topics.existsByCategoryIdAndDeletedAtIsNull(id)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This category still has topics. Delete them or move them to another category first.");
    }
    category.softDelete(Instant.now());
    List<Category> remaining = categories.findAllByDeletedAtIsNullOrderByPositionAsc();
    for (int position = 0; position < remaining.size(); position++) {
      remaining.get(position).reorderTo(position);
    }
  }

  private void validateCoverUpload(Long coverUploadId) {
    if (coverUploadId != null && !uploads.existsById(coverUploadId)) {
      throw new FieldErrorsException(Map.of("coverUploadId", List.of("upload not found")));
    }
  }

  private String uniqueSlug(String name) {
    String base = Slug.of(name, "category");
    String candidate = base;
    int suffix = 2;
    while (categories.existsBySlugAndDeletedAtIsNull(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
