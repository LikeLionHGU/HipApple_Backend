package com.lionapple.storage.dto;

import com.lionapple.storage.Storage;

public record StorageNameResponse(
        Long storageId,
        String name
) {

    public static StorageNameResponse from(Storage storage) {
        return new StorageNameResponse(storage.getStorageId(), storage.getName());
    }
}
