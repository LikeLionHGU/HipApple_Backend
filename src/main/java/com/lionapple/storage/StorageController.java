package com.lionapple.storage;

import java.util.List;

import com.lionapple.common.ApiResult;
import com.lionapple.common.auth.CurrentUserId;
import com.lionapple.storage.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/storage")
@Tag(name = "Storage", description = "저장고 관리 및 AI 품질 분석 API")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    @Operation(summary = "저장고 등록", description = "새로운 저장고를 등록합니다. 사과 입고 날짜, 당도, 경도 등 초기 정보를 함께 저장합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "필수 입력값 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    public ApiResult create(@CurrentUserId Long userId, @Valid @RequestBody StorageRequest request) {
        storageService.create(userId, request);
        return ApiResult.success();
    }

    @GetMapping
    @Operation(summary = "전체 저장고 조회", description = "로그인한 사용자의 저장고 목록을 요약 정보와 함께 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    public List<StorageSummaryResponse> findAll(@CurrentUserId Long userId) {
        return storageService.findAll(userId);
    }

    @GetMapping("/me")
    @Operation(summary = "저장고 이름 목록 조회 (드롭다운용)", description = "로그인한 사용자의 저장고 이름만 리스트로 반환합니다. 프론트 드롭다운 UI에 사용됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    public List<String> myStorageNames(@CurrentUserId Long userId) {
        return storageService.findMyStorageNames(userId);
    }

    @GetMapping("/{storageId}")
    @Operation(summary = "저장고 상세 조회", description = "특정 저장고의 상세 정보를 반환합니다. 품질 판정 결과 및 분석 이력을 포함합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "저장고를 찾을 수 없음")
    })
    public StorageDetailResponse findOne(@CurrentUserId Long userId, @PathVariable Long storageId) {
        return storageService.findOne(userId, storageId);
    }

    @PutMapping("/{storageId}")
    @Operation(summary = "저장고 수정", description = "기존 저장고 정보를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "필수 입력값 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "저장고를 찾을 수 없음")
    })
    public ApiResult update(
            @CurrentUserId Long userId,
            @PathVariable Long storageId,
            @Valid @RequestBody StorageRequest request
    ) {
        storageService.update(userId, storageId, request);
        return ApiResult.success();
    }

    @DeleteMapping("/{storageId}")
    @Operation(summary = "저장고 삭제", description = "저장고를 삭제합니다. 관련된 분석 이력도 함께 삭제됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "저장고를 찾을 수 없음")
    })
    public ApiResult delete(@CurrentUserId Long userId, @PathVariable Long storageId) {
        storageService.delete(userId, storageId);
        return ApiResult.deleted();
    }

    @PostMapping("/{storageId}/analyze")
    @Operation(summary = "저장고 분석 시작", description = "저장고 데이터를 기반으로 AI 분석을 시작하고 결과를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "분석 완료"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "저장고를 찾을 수 없음")
    })
    public ResponseEntity<StorageDetailResponse> startAnalysis(@CurrentUserId Long userId, @PathVariable Long storageId) {
        StorageDetailResponse response = storageService.startAnalysis(userId, storageId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{storageId}/major-schedules")
    @Operation(summary = "주요 일정 조회", description = "저장고의 입고일 기반으로 사과 관리에 필요한 주요 일정 목록을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "저장고를 찾을 수 없음")
    })
    public ResponseEntity<List<MajorScheduleResponse>> getMajorSchedules(
            @CurrentUserId Long userId,
            @PathVariable Long storageId
    ) {
        List<MajorScheduleResponse> response = storageService.getMajorSchedules(userId, storageId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{storageId}/quality-check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "AI 사과 품질 판정", description = "사진을 업로드하면 AI가 사과 품질을 판정합니다. 사진 미제출 시 저장고 데이터(당도·경도·보관일수)만으로 예측합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "품질 판정 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "저장고를 찾을 수 없음"),
            @ApiResponse(responseCode = "503", description = "AI 서버 응답 없음")
    })
    public QualityCheckResponse checkQuality(
            @CurrentUserId Long userId,
            @PathVariable Long storageId,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {
        return storageService.analyzeQuality(userId, storageId, photo);
    }

    @PostMapping(value = "/{storageId}/quality-classify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "이미지 기반 상/중/하 품질 분류 (RandomForest)", description = "이미지와 저장고 데이터를 함께 분석해 상/중/하 등급을 분류합니다. 실험적 기능입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "분류 성공"),
            @ApiResponse(responseCode = "400", description = "이미지 누락"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "저장고를 찾을 수 없음"),
            @ApiResponse(responseCode = "503", description = "AI 서버 응답 없음")
    })
    public QualityClassifyResponse classifyQuality(
            @CurrentUserId Long userId,
            @PathVariable Long storageId,
            @RequestPart("photo") MultipartFile photo
    ) {
        return storageService.classifyQuality(userId, storageId, photo);
    }

    @GetMapping("/{storageId}/quality-status")
    @Operation(summary = "품질 및 저장 환경 현황 조회", description = "저장고의 현재 상태, 시계열 품질 변화 차트, 최근 품질 판정 결과를 통합하여 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료"),
            @ApiResponse(responseCode = "404", description = "저장고를 찾을 수 없음")
    })
    public ResponseEntity<QualityStorageStatusResponse> getQualityStorageStatus(
            @CurrentUserId Long userId,
            @PathVariable Long storageId
    ) {
        QualityStorageStatusResponse response = storageService.getQualityStorageStatus(userId, storageId);
        return ResponseEntity.ok(response);
    }
}
