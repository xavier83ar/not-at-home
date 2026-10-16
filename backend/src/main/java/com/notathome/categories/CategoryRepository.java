package com.notathome.categories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  List<Category> findAllByDeletedAtIsNullOrderByPositionAsc();

  Optional<Category> findBySlugAndDeletedAtIsNull(String slug);

  Optional<Category> findByIdAndDeletedAtIsNull(Long id);

  boolean existsBySlugAndDeletedAtIsNull(String slug);

  long countByDeletedAtIsNull();
}
