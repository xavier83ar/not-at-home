package com.notathome.categories;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-2.3: only admins reach these (enforced in {@code SecurityConfig}, not just here). */
@RestController
class AdminCategoryController {

  private final AdminCategoryService adminCategories;
  private final CategoryService categories;

  AdminCategoryController(AdminCategoryService adminCategories, CategoryService categories) {
    this.adminCategories = adminCategories;
    this.categories = categories;
  }

  @PostMapping("/api/admin/categories")
  @ResponseStatus(HttpStatus.CREATED)
  CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
    return categories.toResponse(adminCategories.create(request));
  }

  @PutMapping("/api/admin/categories/{id}")
  CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
    return categories.toResponse(adminCategories.update(id, request));
  }

  @PutMapping("/api/admin/categories/order")
  void reorder(@Valid @RequestBody ReorderCategoriesRequest request) {
    adminCategories.reorder(request.orderedIds());
  }

  @DeleteMapping("/api/admin/categories/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable Long id) {
    adminCategories.delete(id);
  }
}
