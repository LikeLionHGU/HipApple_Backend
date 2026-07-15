package com.lionapple.price;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lionapple.price.dto.ForecastData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 파이썬 배치가 저장한 forecasts.json을 읽어 캐싱하는 조회 전용 저장소. */
@Component
public class ForecastStore {

    private final ObjectMapper objectMapper;
    private final File file;

    private volatile ForecastData cached;
    private volatile long cachedLastModified = -1;

    public ForecastStore(ObjectMapper objectMapper, @Value("${app.forecast.file}") String path) {
        this.objectMapper = objectMapper;
        this.file = new File(path);
    }

    public Optional<ForecastData> load() {
        if (!file.exists()) {
            return Optional.empty();
        }
        long lastModified = file.lastModified();
        if (cached == null || lastModified != cachedLastModified) {
            synchronized (this) {
                if (cached == null || lastModified != cachedLastModified) {
                    try {
                        cached = objectMapper.readValue(file, ForecastData.class);
                        cachedLastModified = lastModified;
                    } catch (IOException exception) {
                        return Optional.ofNullable(cached);
                    }
                }
            }
        }
        return Optional.of(cached);
    }

    public ForecastData loadOrThrow() {
        return load().orElseThrow(
                () -> new ForecastNotFoundException("예측 데이터가 아직 생성되지 않았습니다."));
    }
}
