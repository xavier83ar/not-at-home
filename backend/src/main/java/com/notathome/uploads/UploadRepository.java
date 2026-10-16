package com.notathome.uploads;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<Upload, Long> {

  Optional<Upload> findByStorageKey(String storageKey);
}
