package com.notathome.categories;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class CategoryController {

  private final CategoryService categories;

  CategoryController(CategoryService categories) {
    this.categories = categories;
  }

  /** FR-2.2: the "All categories" page. */
  @GetMapping("/api/categories")
  List<CategoryResponse> list() {
    return categories.list();
  }

  @GetMapping("/api/categories/{slug}")
  CategoryResponse getBySlug(@PathVariable String slug) {
    return categories
        .getBySlug(slug)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
