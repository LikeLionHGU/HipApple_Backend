package com.lionapple.storage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageRepository extends JpaRepository<Storage, Long> {

    List<Storage> findAllByUserId(Long userId);

    Optional<Storage> findByStorageIdAndUserId(Long storageId, Long userId);
}
