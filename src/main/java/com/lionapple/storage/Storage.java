package com.lionapple.storage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.lionapple.storage.dto.QualityAnalysisResult;
import com.lionapple.storage.dto.StorageRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "storages")
public class Storage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long storageId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String appleType;

    @Column(nullable = false)
    private LocalDateTime storeDate;

    @Column(nullable = false)
    private String storageMethod;

    @Column(nullable = false)
    private int brix;

    @Column(nullable = false)
    private int hardness;

    @Column(nullable = false, name = "storage_condition")
    private String condition;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private String preferredDate;

    private String qualityGrade;

    @Column(columnDefinition = "TEXT")
    private String qualityRipeness;

    @Column(columnDefinition = "TEXT")
    private String qualityColorDescription;

    @Column(columnDefinition = "TEXT")
    private String qualityShipmentComment;

    private String qualityConfidence;

    private LocalDateTime qualityCheckedAt;

    @Column(nullable = true)
    private LocalDate analysisStartDate; // 분석 시작일

    protected Storage() {
    }

    public Storage(Long userId, StorageRequest request) {
        this.userId = userId;
        update(request);
    }

    public Long getStorageId() {
        return storageId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getAppleType() {
        return appleType;
    }

    public LocalDateTime getStoreDate() {
        return storeDate;
    }

    public String getStorageMethod() {
        return storageMethod;
    }

    public int getBrix() {
        return brix;
    }

    public int getHardness() {
        return hardness;
    }

    public String getCondition() {
        return condition;
    }

    public int getAmount() {
        return amount;
    }

    public String getPreferredDate() {
        return preferredDate;
    }

    public LocalDate getAnalysisStartDate(){return analysisStartDate;}

    public String getQualityGrade() {
        return qualityGrade;
    }

    public String getQualityRipeness() {
        return qualityRipeness;
    }

    public String getQualityColorDescription() {
        return qualityColorDescription;
    }

    public String getQualityShipmentComment() {
        return qualityShipmentComment;
    }

    public String getQualityConfidence() {
        return qualityConfidence;
    }

    public LocalDateTime getQualityCheckedAt() {
        return qualityCheckedAt;
    }

    public void applyQualityCheck(QualityAnalysisResult result, LocalDateTime checkedAt) {
        this.qualityGrade = result.grade();
        this.qualityRipeness = result.ripeness();
        this.qualityColorDescription = result.colorDescription();
        this.qualityShipmentComment = result.shipmentComment();
        this.qualityConfidence = result.confidence();
        this.qualityCheckedAt = checkedAt;
    }

    public void update(StorageRequest request) {
        this.name = request.name();
        this.appleType = request.appleType();
        this.storeDate = request.storeDate();
        this.storageMethod = request.storageMethod();
        this.brix = request.brix();
        this.hardness = request.hardness();
        this.condition = request.condition();
        this.amount = request.amount();
        this.preferredDate = request.preferredDate();
    }
    public void startAnalysis() {
        if (this.analysisStartDate == null) {
            this.analysisStartDate = LocalDate.now();
        }
    }

    // 분석 진행 기간(일수) 계산 (분석 시작 전이면 0일 반환)
    public long getStoragePeriodDays() {
        if (this.analysisStartDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(this.analysisStartDate, LocalDate.now()) + 1;
    }
}
