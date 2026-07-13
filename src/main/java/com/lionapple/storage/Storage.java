package com.lionapple.storage;

import java.time.LocalDateTime;

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

    protected Storage() {
    }

    public Storage(StorageRequest request) {
        update(request);
    }

    public Long getStorageId() {
        return storageId;
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
}
