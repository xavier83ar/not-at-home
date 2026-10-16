package com.notathome.categories;

import com.notathome.uploads.Upload;
import com.notathome.uploads.UploadRepository;
import com.notathome.uploads.UploadUrl;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

  private final CategoryRepository categories;
  private final UploadRepository uploads;

  CategoryService(CategoryRepository categories, UploadRepository uploads) {
    this.categories = categories;
    this.uploads = uploads;
  }

  /** FR-2.2: every active category, in display order. */
  public List<CategoryResponse> list() {
    List<Category> found = categories.findAllByDeletedAtIsNullOrderByPositionAsc();
    Map<Long, String> coverUrls = coverUrls(found);
    return found.stream()
        .map(
            category -> CategoryResponse.from(category, coverUrls.get(category.getCoverUploadId())))
        .toList();
  }

  public Optional<CategoryResponse> getBySlug(String slug) {
    return categories.findBySlugAndDeletedAtIsNull(slug).map(this::toResponse);
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
