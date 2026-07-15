package com.lionapple.storage;

import java.util.List;

import com.lionapple.common.ApiResult;
import com.lionapple.common.auth.CurrentUserId;
import com.lionapple.storage.dto.StorageDetailResponse;
import com.lionapple.storage.dto.StorageRequest;
import com.lionapple.storage.dto.StorageSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/storage")
@Tag(name = "Storage", description = "저장고 API")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    @Operation(summary = "저장고 등록")
    public ApiResult create(@CurrentUserId Long userId, @Valid @RequestBody StorageRequest request) {
        storageService.create(userId, request);
        return ApiResult.success();
    }

    @GetMapping
    @Operation(summary = "전체 저장고 조회")
    public List<StorageSummaryResponse> findAll(@CurrentUserId Long userId) {
        return storageService.findAll(userId);
    }

    @GetMapping("/me")
    @Operation(summary = "저장고 이름 목록 조회 (드롭다운용)")
    public List<String> myStorageNames(@CurrentUserId Long userId) {
        return storageService.findMyStorageNames(userId);
    }

    @GetMapping("/{storageId}")
    @Operation(summary = "세부적인 저장고 조회")
    public StorageDetailResponse findOne(@CurrentUserId Long userId, @PathVariable Long storageId) {
        return storageService.findOne(userId, storageId);
    }

    @PutMapping("/{storageId}")
    @Operation(summary = "저장고 수정")
    public ApiResult update(
            @CurrentUserId Long userId,
            @PathVariable Long storageId,
            @Valid @RequestBody StorageRequest request
    ) {
        storageService.update(userId, storageId, request);
        return ApiResult.success();
    }

    @DeleteMapping("/{storageId}")
    @Operation(summary = "저장고 삭제")
    public ApiResult delete(@CurrentUserId Long userId, @PathVariable Long storageId) {
        storageService.delete(userId, storageId);
        return ApiResult.deleted();
    }
}
