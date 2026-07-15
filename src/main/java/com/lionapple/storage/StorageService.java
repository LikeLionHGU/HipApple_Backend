package com.lionapple.storage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import com.lionapple.storage.dto.StorageDetailResponse;
import com.lionapple.storage.dto.StorageNameResponse;
import com.lionapple.storage.dto.StorageRequest;
import com.lionapple.storage.dto.StorageSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StorageService {

    private final StorageRepository storageRepository;

    public StorageService(StorageRepository storageRepository) {
        this.storageRepository = storageRepository;
    }

    @Transactional
    public void create(Long userId, StorageRequest request) {
        storageRepository.save(new Storage(userId, request));
    }

    public StorageNameResponse findMyStorageName(Long userId) {
        return storageRepository.findFirstByUserIdOrderByStorageIdAsc(userId)
                .map(storage -> new StorageNameResponse(storage.getName()))
                .orElseThrow(() -> new NoSuchElementException("저장고를 찾을 수 없습니다."));
    }

    public List<StorageSummaryResponse> findAll(Long userId) {
        return storageRepository.findAllByUserId(userId).stream()
                .map(StorageSummaryResponse::from)
                .toList();
    }

    public StorageDetailResponse findOne(Long userId, Long storageId) {
        Storage storage = getStorage(userId, storageId);
        long storagePeriodDays = ChronoUnit.DAYS.between(storage.getStoreDate().toLocalDate(), LocalDate.now());
        if (storagePeriodDays < 0) {
            storagePeriodDays = 0;
        }

        int temperature = "CA".equalsIgnoreCase(storage.getStorageMethod()) ? 1 : 4;
        int humidity = 92;
        double ethylene = storage.getStorageMethod().equalsIgnoreCase("CA") ? 0.3 : 0.8;
        String qualityStatus = storage.getCondition() + " / 출하 가능";
        String analysisReason = "당도 " + storage.getBrix() + "Brix, 경도 " + storage.getHardness()
                + "kgf, 저장방식 " + storage.getStorageMethod() + " 기준으로 품질 상태를 산정했습니다.";

        return new StorageDetailResponse(
                storage.getStorageId(),
                storage.getName(),
                storage.getAppleType(),
                toYyyyMMdd(storage.getStoreDate()),
                storage.getStoreDate(),
                storage.getStorageMethod(),
                storage.getBrix(),
                storage.getHardness(),
                storage.getCondition(),
                storage.getAmount(),
                storage.getPreferredDate(),
                storagePeriodDays,
                temperature,
                humidity,
                ethylene,
                qualityStatus,
                storage.getPreferredDate(),
                analysisReason,
                nearbyDates(storage.getStoreDate().toLocalDate())
        );
    }

    @Transactional
    public void update(Long userId, Long storageId, StorageRequest request) {
        getStorage(userId, storageId).update(request);
    }

    @Transactional
    public void delete(Long userId, Long storageId) {
        storageRepository.delete(getStorage(userId, storageId));
    }

    private Storage getStorage(Long userId, Long storageId) {
        return storageRepository.findByStorageIdAndUserId(storageId, userId)
                .orElseThrow(() -> new NoSuchElementException("저장고를 찾을 수 없습니다."));
    }

    private static int toYyyyMMdd(LocalDateTime dateTime) {
        LocalDate date = dateTime.toLocalDate();
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    private static List<Integer> nearbyDates(LocalDate date) {
        List<Integer> dates = new ArrayList<>();
        for (int offset = -2; offset <= 2; offset++) {
            LocalDate nearby = date.plusDays(offset);
            dates.add(nearby.getYear() * 10000 + nearby.getMonthValue() * 100 + nearby.getDayOfMonth());
        }
        return dates;
    }
}
