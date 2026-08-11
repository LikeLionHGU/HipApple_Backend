package com.lionapple.storage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import java.util.Comparator;

import com.lionapple.storage.dto.MajorScheduleResponse;
import com.lionapple.storage.dto.QualityAnalysisResult;
import com.lionapple.storage.dto.QualityCheckResponse;
import com.lionapple.storage.dto.QualityClassifyResponse;
import com.lionapple.storage.dto.QualityClassifyResult;
import com.lionapple.storage.dto.ShipmentAnalysisResponse;
import com.lionapple.storage.dto.StorageDetailResponse;
import com.lionapple.storage.dto.StorageRequest;
import com.lionapple.storage.dto.StorageSummaryResponse;
import com.lionapple.price.PriceService;
import com.lionapple.price.dto.PriceDashboardResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class StorageService {

    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_PHOTO_BYTES = 8L * 1024 * 1024;

    private final StorageRepository storageRepository;
    private final QualityAnalysisClient qualityAnalysisClient;
    private final QualityClassifierClient qualityClassifierClient;
    private final PriceService priceService;

    public StorageService(
            StorageRepository storageRepository,
            QualityAnalysisClient qualityAnalysisClient,
            QualityClassifierClient qualityClassifierClient,
            PriceService priceService
    ) {
        this.storageRepository = storageRepository;
        this.qualityAnalysisClient = qualityAnalysisClient;
        this.qualityClassifierClient = qualityClassifierClient;
        this.priceService = priceService;
    }

    @Transactional
    public void create(Long userId, StorageRequest request) {
        storageRepository.save(new Storage(userId, request));
    }

    public List<String> findMyStorageNames(Long userId) {
        return storageRepository.findAllByUserId(userId).stream()
                .map(Storage::getName)
                .toList();
    }

    public List<StorageSummaryResponse> findAll(Long userId) {
        return storageRepository.findAllByUserId(userId).stream()
                .map(StorageSummaryResponse::from)
                .toList();
    }

    public StorageDetailResponse findOne(Long userId, Long storageId) {
        Storage storage = getStorage(userId, storageId);
        long storagePeriodDays = storagePeriodDays(storage);

        int temperature = "CA".equalsIgnoreCase(storage.getStorageMethod()) ? 1 : 4;
        int humidity = 92;
        double ethylene = storage.getStorageMethod().equalsIgnoreCase("CA") ? 0.3 : 0.8;
        String qualityStatus = storage.getCondition() + " / 출하 가능";
        String analysisReason = "당도 " + storage.getBrix() + "Brix, 경도 " + storage.getHardness()
                + "kgf, 저장방식 " + storage.getStorageMethod() + " 기준으로 품질 상태를 산정했습니다.";
        String priceRecommendationReason = "";

        String shipmentRecommendation = "분석 전";
        List<ShipmentAnalysisResponse> shipmentAnalyses = new ArrayList<>();

        if (storage.getAnalysisStartDate() != null) {
            PriceDashboardResponse priceData = null;
            try {
                priceData = priceService.getMarketDashboardData(LocalDate.now().toString(), "110001", "0601", "01");
            } catch (Exception e) {
                // Ignore exception and use fallback
            }

            LocalDate bestDate = storage.getAnalysisStartDate().plusDays(5);
            int bestPrice = 0;

            if (priceData != null && priceData.future_chart_data != null) {
                for (PriceDashboardResponse.ChartData data : priceData.future_chart_data) {
                    LocalDate d = LocalDate.parse(data.date);
                    if (data.price > bestPrice) {
                        bestPrice = data.price;
                        bestDate = d;
                    }
                }
            }

            int todayPrice = bestPrice;
            if (priceData != null && priceData.future_chart_data != null) {
                for (PriceDashboardResponse.ChartData data : priceData.future_chart_data) {
                    if (data.date.equals(LocalDate.now().toString())) {
                        todayPrice = data.price;
                        break;
                    }
                }
            }

            shipmentRecommendation = bestDate.toString();
            String formattedDate = bestDate.getMonthValue() + "월 " + bestDate.getDayOfMonth() + "일";
            long diff = (long)(bestPrice - todayPrice) * storage.getAmount();
            long diffManWon = diff / 10000;

            if (diffManWon > 0) {
                priceRecommendationReason = String.format("오늘 출하 시보다 약 %,d만 원 높은 기대 매출이 예상되어, %s 출하를 추천해요", diffManWon, formattedDate);
            } else {
                priceRecommendationReason = String.format("현재 가격이 가장 좋은 시기입니다. 오늘(%s) 출하를 추천해요", formattedDate);
            }
            
            shipmentAnalyses = futureDates(priceData);
        } else {
            shipmentAnalyses = futureDates(null);
        }

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
                shipmentRecommendation,
                analysisReason,
                priceRecommendationReason,
                shipmentAnalyses,
                storage.getQualityGrade(),
                storage.getQualityRipeness(),
                storage.getQualityColorDescription(),
                storage.getQualityShipmentComment(),
                storage.getQualityConfidence(),
                storage.getQualityCheckedAt()
        );
    }

    @Transactional
    public StorageDetailResponse startAnalysis(Long userId, Long storageId) {
        Storage storage = getStorage(userId, storageId);

        // 분석 시작일 설정
        storage.startAnalysis();

        // 기존에 만드신 상세 조회 메서드명(findOne)으로 호출
        return findOne(userId, storageId);
    }

    public List<MajorScheduleResponse> getMajorSchedules(Long userId, Long storageId) {
        Storage storage = getStorage(userId, storageId);

        List<MajorScheduleResponse> majorSchedules = new ArrayList<>();

        // 1. 저장 시작 (저장고 등록 시 기본 생성)
        if (storage.getStoreDate() != null) {
            majorSchedules.add(new MajorScheduleResponse(
                    "저장 시작",
                    storage.getStoreDate().toLocalDate(),
                    "STORE_START"
            ));
        }

        // 2. 'AI 추천 받기'를 클릭해 analysisStartDate가 생성된 경우
        if (storage.getAnalysisStartDate() != null) {
            LocalDate analysisStart = storage.getAnalysisStartDate();

            // 첫 AI 가격 예측 생성
            majorSchedules.add(new MajorScheduleResponse(
                    "첫 AI 가격 예측 생성",
                    analysisStart,
                    "FIRST_PREDICT"
            ));

            // 첫 '출하 고려' 추천 (희망 출하일이 있으면 사용, 없으면 분석 시작 5일 후)
            LocalDate shipmentDate = (storage.getPreferredDate() != null && !storage.getPreferredDate().isEmpty() && !storage.getPreferredDate().equals("미정"))
                    ? LocalDate.parse(storage.getPreferredDate())
                    : analysisStart.plusDays(5);

            majorSchedules.add(new MajorScheduleResponse(
                    "첫 '출하 고려' 추천",
                    shipmentDate,
                    "SHIPMENT_RECOMMEND"
            ));

            // 최고 예측 가격 기록 (분석 시작 15일 후)
            majorSchedules.add(new MajorScheduleResponse(
                    "최고 예측 가격 기록",
                    analysisStart.plusDays(15),
                    "MAX_PRICE_RECORD"
            ));

            // 리포트 생성 (분석 시작 20일 후)
            majorSchedules.add(new MajorScheduleResponse(
                    "리포트 생성",
                    analysisStart.plusDays(20),
                    "REPORT_GENERATE"
            ));
        }

        // 날짜순 오름차순 정렬 후 반환
        return majorSchedules.stream()
                .sorted(Comparator.comparing(MajorScheduleResponse::date))
                .toList();
    }

    @Transactional
    public QualityCheckResponse analyzeQuality(Long userId, Long storageId, MultipartFile photo) {
        Storage storage = getStorage(userId, storageId);
        validatePhoto(photo);

        QualityAnalysisResult result = qualityAnalysisClient.analyze(photo, storage);
        LocalDateTime checkedAt = LocalDateTime.now();
        storage.applyQualityCheck(result, checkedAt);

        return QualityCheckResponse.of(storageId, checkedAt, result);
    }

    /** 이미지 특징 + Storage 필드(brix/hardness/storageMethod/저장일수/amount) 기반 실험적 상/중/하 분류.
     * quality_classifier(RandomForest) 모듈 호출 — analyzeQuality(gpt-4o-mini)와 달리 결과를 저장하지 않는다. */
    public QualityClassifyResponse classifyQuality(Long userId, Long storageId, MultipartFile photo) {
        Storage storage = getStorage(userId, storageId);
        validatePhoto(photo);

        QualityClassifyResult result = qualityClassifierClient.classify(
                photo,
                storage.getBrix(),
                storage.getHardness(),
                storage.getStorageMethod(),
                storagePeriodDays(storage),
                storage.getAmount()
        );
        return QualityClassifyResponse.of(storageId, result);
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

    private static long storagePeriodDays(Storage storage) {
        long days = ChronoUnit.DAYS.between(storage.getStoreDate().toLocalDate(), LocalDate.now());
        return Math.max(days, 0);
    }

    private static void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return; // 사진 미제출 허용
        }
        if (!ALLOWED_PHOTO_TYPES.contains(photo.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jpeg/png/webp 이미지만 업로드할 수 있습니다.");
        }
        if (photo.getSize() > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 용량은 8MB를 초과할 수 없습니다.");
        }
    }

    private static int toYyyyMMdd(LocalDateTime dateTime) {
        LocalDate date = dateTime.toLocalDate();
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    private static List<ShipmentAnalysisResponse> futureDates(PriceDashboardResponse priceData) {
        List<ShipmentAnalysisResponse> list = new ArrayList<>();
        
        int avgPrice = 2000;
        if (priceData != null && priceData.price_summary != null) {
            avgPrice = priceData.price_summary.weekly_average_price;
        }

        if (priceData != null && priceData.future_chart_data != null) {
            for (PriceDashboardResponse.ChartData cd : priceData.future_chart_data) {
                int price = cd.price;
                LocalDate d = LocalDate.parse(cd.date);
                String status = price > avgPrice ? "우수" : (price < avgPrice - 500 ? "불량" : "양호");
                String event = (d.getMonthValue() == 9 && (d.getDayOfMonth() >= 15 && d.getDayOfMonth() <= 17)) ? "명절" : null;
                list.add(new ShipmentAnalysisResponse(cd.date, price, status, event));
            }
        } else {
            for (int i = 0; i < 7; i++) {
                LocalDate d = LocalDate.now().plusDays(i);
                int price = avgPrice + (i * 100);
                String status = price > avgPrice ? "우수" : (price < avgPrice - 500 ? "불량" : "양호");
                String event = (d.getMonthValue() == 9 && (d.getDayOfMonth() >= 15 && d.getDayOfMonth() <= 17)) ? "명절" : null;
                list.add(new ShipmentAnalysisResponse(d.toString(), price, status, event));
            }
        }
        return list;
    }
}
