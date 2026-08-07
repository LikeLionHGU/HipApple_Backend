package com.lionapple.storage;

import java.io.IOException;

import com.lionapple.storage.dto.QualityAnalysisResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 저장고 사진을 파이썬 AI 서버(/api/quality/analyze)로 전달해 품질 판정을 받아오는 클라이언트. */
@Component
public class QualityAnalysisClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String aiServerUrl;

    public QualityAnalysisClient(@Value("${app.ai-server.url}") String aiServerUrl) {
        this.aiServerUrl = aiServerUrl;
    }

    public QualityAnalysisResult analyze(MultipartFile photo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("photo", toResource(photo));

        try {
            return restTemplate.postForObject(
                    aiServerUrl + "/api/quality/analyze",
                    new HttpEntity<>(body, headers),
                    QualityAnalysisResult.class
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 분석 서버 통신 실패: " + exception.getMessage());
        }
    }

    private static ByteArrayResource toResource(MultipartFile photo) {
        try {
            byte[] bytes = photo.getBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return photo.getOriginalFilename();
                }
            };
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 읽을 수 없습니다.");
        }
    }
}
