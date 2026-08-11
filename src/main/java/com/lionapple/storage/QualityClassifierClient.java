package com.lionapple.storage;

import java.io.IOException;

import com.lionapple.storage.dto.QualityClassifyResult;
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

/** 저장고 사진 + Storage 탭형 필드를 파이썬 AI 서버(/api/quality/classify)로 전달해
 * RandomForest 기반 상/중/하 분류 결과를 받아오는 클라이언트. (실험적, quality_classifier 모듈) */
@Component
public class QualityClassifierClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String aiServerUrl;

    public QualityClassifierClient(@Value("${app.ai-server.url}") String aiServerUrl) {
        this.aiServerUrl = aiServerUrl;
    }

    public QualityClassifyResult classify(
            MultipartFile photo, int brix, int hardness, String storageMethod, long storageDays, int amount
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("photo", toResource(photo));
        body.add("brix", brix);
        body.add("hardness", hardness);
        body.add("storage_method", storageMethod);
        body.add("storage_days", storageDays);
        body.add("amount", amount);

        try {
            return restTemplate.postForObject(
                    aiServerUrl + "/api/quality/classify",
                    new HttpEntity<>(body, headers),
                    QualityClassifyResult.class
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "품질 분류 서버 통신 실패: " + exception.getMessage());
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
