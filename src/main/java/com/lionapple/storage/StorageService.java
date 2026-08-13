package com.lionapple.storage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import java.util.Comparator;

import com.lionapple.storage.dto.*;
import com.lionapple.price.PriceService;
import com.lionapple.price.dto.PriceDashboardResponse;
import com.lionapple.price.dto.PriceFutureCommentsResponse;
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
        PriceDashboardResponse priceData = null;
        PriceFutureCommentsResponse futureCommentsData = null;

        if (storage.getAnalysisStartDate() != null) {
            try {
                priceData = priceService.getMarketDashboardData(LocalDate.now().toString(), "110001", "0601", "01");
            } catch (Exception e) {
                // Ignore exception and use fallback
            }

            try {
                futureCommentsData = priceService.getFutureComments(LocalDate.now().toString(), "110001", "0601", "01");
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
                priceRecommendationReason = String.format("현재 가격이 가장 좋은 시기입니다. (%s) 출하를 추천해요", formattedDate);
            }
            
            shipmentAnalyses = futureDates(priceData);
        } else {
            shipmentAnalyses = futureDates(null);
        }

        int maxPredictedPrice = 4910;
        int minPredictedPrice = 3850;
        int avgPredictedPrice = 4438;
        int priceIncreaseDays = 0;
        int priceDecreaseDays = 0;
        
        if (priceData != null) {
            List<PriceDashboardResponse.ChartData> combinedChart = new ArrayList<>();
            if (priceData.chart_data != null) combinedChart.addAll(priceData.chart_data);
            if (priceData.future_chart_data != null) combinedChart.addAll(priceData.future_chart_data);
            
            for (int i = 1; i < combinedChart.size(); i++) {
                if (combinedChart.get(i).price > combinedChart.get(i-1).price) priceIncreaseDays++;
                else if (combinedChart.get(i).price < combinedChart.get(i-1).price) priceDecreaseDays++;
            }
        }
        
        if (storage.getAnalysisStartDate() != null && shipmentAnalyses != null && !shipmentAnalyses.isEmpty()) {
            maxPredictedPrice = shipmentAnalyses.stream().mapToInt(ShipmentAnalysisResponse::predictedPrice).max().orElse(maxPredictedPrice);
            minPredictedPrice = shipmentAnalyses.stream().mapToInt(ShipmentAnalysisResponse::predictedPrice).min().orElse(minPredictedPrice);
            avgPredictedPrice = (int) shipmentAnalyses.stream().mapToInt(ShipmentAnalysisResponse::predictedPrice).average().orElse(avgPredictedPrice);
        }

        AnalysisPeriodSummaryResponse periodSummary = new AnalysisPeriodSummaryResponse(
                new AnalysisPeriodSummaryResponse.AiAnalysisSummary(
                        storage.getAnalysisCount(), storage.getRecommendationCount(), maxPredictedPrice, minPredictedPrice, avgPredictedPrice, priceIncreaseDays, priceDecreaseDays
                ),
                new AnalysisPeriodSummaryResponse.StorageEnvironmentSummary(
                        1.8, 91, 1650, 2, 1, 0, 94
                )
        );

        List<MarketAnalysisRecordResponse> marketAnalysisRecords = new ArrayList<>();
        if (futureCommentsData != null && futureCommentsData.future_reports != null) {
            for (PriceFutureCommentsResponse.FutureReport r : futureCommentsData.future_reports) {
                marketAnalysisRecords.add(new MarketAnalysisRecordResponse(r.date, r.content));
            }
        } else {
            // Fallback (dummy data for 7 days)
            for (int i = 0; i < 7; i++) {
                LocalDate d = LocalDate.now().plusDays(i);
                String formattedDate = d.getYear() + "." + String.format("%02d", d.getMonthValue()) + "." + String.format("%02d", d.getDayOfMonth());
                String content = (i % 2 == 0) ? "기온 상승으로 인한 소비 증가가 가격에 일부 반영될 것으로 예측됩니다.(HARDCoding)" : "사전 물량 확보 수요 증가로 가격이 일시적인 강세를 보일 수 있습니다.(HARDCODING)";
                marketAnalysisRecords.add(new MarketAnalysisRecordResponse(formattedDate, content));
            }
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
                storage.getQualityCheckedAt(),
                periodSummary,
                marketAnalysisRecords
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
    /**
     * '품질 및 저장 환경 변화' 카드 및 저장고 현황 조회
     */
    public QualityStorageStatusResponse getQualityStorageStatus(Long userId, Long storageId) {
        Storage storage = getStorage(userId, storageId);
        long storagePeriodDays = storagePeriodDays(storage); // DB storeDate 기준 동적 계산 (23일 등)

        // 1. 저장고 현황 (온도: 2°C, 습도: 90%, 에틸렌: 0.3ppm 하드코딩 / 저장기간만 DB 계산)
        QualityStorageStatusResponse.StorageEnvironment environment = new QualityStorageStatusResponse.StorageEnvironment(
                storage.getName(), // 저장고 이름 (예: "A동")
                2.0,               // 온도 (°C)
                90.0,              // 습도 (%)
                0.3,               // 에틸렌 (ppm)
                storagePeriodDays, // DB storeDate 기반 계산된 저장기간
                LocalDate.now().getYear() + "." + LocalDate.now().getMonthValue() + "." + LocalDate.now().getDayOfMonth() // 마지막 측정일
        );

        // 2. 좌측 차트: 품질 점수 변화 추이 (샘플 데이터)
        List<QualityStorageStatusResponse.QualityTrendPoint> trendData = List.of(
                new QualityStorageStatusResponse.QualityTrendPoint("02.15", 98.0),
                new QualityStorageStatusResponse.QualityTrendPoint("03.15", 96.5),
                new QualityStorageStatusResponse.QualityTrendPoint("04.15", 95.0),
                new QualityStorageStatusResponse.QualityTrendPoint("05.15", 93.8),
                new QualityStorageStatusResponse.QualityTrendPoint("06.15", 92.5),
                new QualityStorageStatusResponse.QualityTrendPoint("07.15", 91.8),
                new QualityStorageStatusResponse.QualityTrendPoint("08.02", 91.0)
        );

        // 3. 우측 카드: 현재 품질 정보 (storage의 condition 또는 qualityGrade 반영 가능)
        String currentGrade = storage.getCondition() != null ? storage.getCondition() : "우수";
        QualityStorageStatusResponse.CurrentMetrics currentMetrics = new QualityStorageStatusResponse.CurrentMetrics(
                currentGrade, // "우수"
                91,           // 품질 점수
                100,          // 만점 (100)
                18,           // 예상 저장 가능 기간(일)
                "보통"        // 품질 저하 속도
        );

        return new QualityStorageStatusResponse(
                "success",
                "3. 품질 및 저장 환경 변화",
                environment,
                trendData,
                currentMetrics
        );
    }
}
