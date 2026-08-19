package com.lionapple;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.lionapple.storage.QualityAnalysisClient;
import com.lionapple.storage.QualityClassifierClient;
import com.lionapple.storage.dto.QualityAnalysisResult;
import com.lionapple.storage.dto.QualityClassifyResult;
import com.lionapple.user.GoogleTokenVerifier;
import com.lionapple.user.dto.GoogleUserInfo;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSpecificationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @MockBean
    private QualityAnalysisClient qualityAnalysisClient;

    @MockBean
    private QualityClassifierClient qualityClassifierClient;

    private String login() throws Exception {
        when(googleTokenVerifier.verify(eq("google-id-token")))
                .thenReturn(new GoogleUserInfo("google-sub-1", "jua@example.com", "박주아", "https://example.com/profile.png"));

        String response = mockMvc.perform(post("/user/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", matchesPattern("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + JsonPath.<String>read(response, "$.accessToken");
    }

    @Test
    void userApisMatchSpecification() throws Exception {
        String token = login();

        mockMvc.perform(post("/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "farmName":"청송 사과농장",
                                  "farmLocation":"경북 청송군",
                                  "variety":"부사",
                                  "farmSize":300,
                                  "farmSizeUnit":"고루",
                                  "shipmentType":"도매시장"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Success"));

        mockMvc.perform(get("/user/me")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("청송 사과농장"));

        // Add test for UI payload (missing farmSizeUnit, string farmSize)
        mockMvc.perform(post("/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "farmName":"농가이름",
                                  "variety":"후지",
                                  "farmSize":"100",
                                  "shipmentType":"도매시장"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void storageApisMatchSpecification() throws Exception {
        String token = login();

        String request = """
                {
                  "name":"저장고A",
                  "appleType":"부사 시스코",
                  "storeDate":"2026-07-01T00:00:00",
                  "storageMethod":"CA",
                  "brix":15,
                  "hardness":10,
                  "condition":"우수",
                  "amount":5,
                  "preferredDate":"12월 중순"
                }
                """;

        mockMvc.perform(post("/storage")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Success"));

        mockMvc.perform(get("/storage/me")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value("저장고A"));

        String listResponse = mockMvc.perform(get("/storage")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].storageId").exists())
                .andExpect(jsonPath("$[0].name").value("저장고A"))
                .andExpect(jsonPath("$[0].startDate").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        long storageId = ((Number) JsonPath.read(listResponse, "$[0].storageId")).longValue();

        mockMvc.perform(get("/storage/" + storageId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").exists())
                .andExpect(jsonPath("$.humidity").exists())
                .andExpect(jsonPath("$.ethylene").exists());

        mockMvc.perform(put("/storage/" + storageId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Success"));

        mockMvc.perform(delete("/storage/" + storageId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("삭제 완료"));
    }

    @Test
    void storageQualityCheckReturnsAiAnalysis() throws Exception {
        String token = login();

        mockMvc.perform(post("/storage")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"저장고B",
                                  "appleType":"부사",
                                  "storeDate":"2026-07-01T00:00:00",
                                  "storageMethod":"CA",
                                  "brix":15,
                                  "hardness":10,
                                  "condition":"우수",
                                  "amount":5,
                                  "preferredDate":"12월 중순"
                                }
                                """))
                .andExpect(status().isOk());

        String listResponse = mockMvc.perform(get("/storage")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        List<Number> storageIds = JsonPath.read(listResponse, "$[?(@.name=='저장고B')].storageId");
        long storageId = storageIds.get(0).longValue();

        when(qualityAnalysisClient.analyze(any(), any())).thenReturn(new QualityAnalysisResult(
                "특", "완숙 직전(약 90%)", "붉은빛이 고르게 퍼져 있으며 광택 양호", "1주일 이내 출하 권장", "medium"));

        MockMultipartFile photo = new MockMultipartFile("photo", "apple.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/storage/" + storageId + "/quality-check")
                        .file(photo)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageId").value(storageId))
                .andExpect(jsonPath("$.grade").value("특"))
                .andExpect(jsonPath("$.confidence").value("medium"))
                .andExpect(jsonPath("$.disclaimer").exists());

        mockMvc.perform(delete("/storage/" + storageId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    @Test
    void storageQualityClassifyReturnsLabelAndProbabilities() throws Exception {
        String token = login();

        mockMvc.perform(post("/storage")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"저장고C",
                                  "appleType":"부사",
                                  "storeDate":"2026-07-01T00:00:00",
                                  "storageMethod":"CA",
                                  "brix":15,
                                  "hardness":10,
                                  "condition":"우수",
                                  "amount":5,
                                  "preferredDate":"12월 중순"
                                }
                                """))
                .andExpect(status().isOk());

        String listResponse = mockMvc.perform(get("/storage")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        List<Number> storageIds = JsonPath.read(listResponse, "$[?(@.name=='저장고C')].storageId");
        long storageId = storageIds.get(0).longValue();

        when(qualityClassifierClient.classify(any(), eq(15), eq(10), eq("CA"), anyLong(), eq(5)))
                .thenReturn(new QualityClassifyResult(
                        "상",
                        Map.of("상", 0.7, "중", 0.2, "하", 0.1),
                        List.of(new QualityClassifyResult.FeatureContribution("mean_saturation", 0.14))));

        MockMultipartFile photo = new MockMultipartFile("photo", "apple.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/storage/" + storageId + "/quality-classify")
                        .file(photo)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("상"))
                .andExpect(jsonPath("$.probabilities.상").value(0.7))
                .andExpect(jsonPath("$.topFeatures[0].name").value("mean_saturation"));

        mockMvc.perform(delete("/storage/" + storageId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    @Test
    void priceForecastApisMatchSpecification() throws Exception {
        String token = login();

        mockMvc.perform(get("/price/options")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markets[0]").value("서울가락"))
                .andExpect(jsonPath("$.varieties[0]").value("후지"));

        mockMvc.perform(get("/price/forecast")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("market", "서울가락")
                        .param("variety", "후지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("서울가락"))
                .andExpect(jsonPath("$.variety").value("후지"))
                .andExpect(jsonPath("$.unit").value("원/kg"))
                .andExpect(jsonPath("$.asOf").value("2026-07-15"))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.forecast[0].horizon").value(1))
                .andExpect(jsonPath("$.forecast[0].low").exists())
                .andExpect(jsonPath("$.forecast[0].high").exists());

        mockMvc.perform(get("/price/forecast")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("market", "서울가락"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/price/forecast")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("market", "없는시장")
                        .param("variety", "없는품종"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 도매시장·품종의 예측 데이터가 없습니다."));

        mockMvc.perform(get("/price/me")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedBy").exists())
                .andExpect(jsonPath("$.unit").value("원/kg"))
                .andExpect(jsonPath("$.forecast[0].price").exists());
    }

    @Test
    void loginResponseMarksNewAndExistingUser() throws Exception {
        when(googleTokenVerifier.verify(eq("fresh-google-id-token")))
                .thenReturn(new GoogleUserInfo("google-sub-fresh", "fresh@example.com", "신규유저", null));

        mockMvc.perform(post("/user/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"fresh-google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true));

        mockMvc.perform(post("/user/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"fresh-google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(false));
    }

    @Test
    void protectedApisRejectMissingOrInvalidToken() throws Exception {
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/storage"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/storage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/price/dashboard")
                        .param("date", "2026-07-15")
                        .param("market_code", "110001")
                        .param("item_code", "0601")
                        .param("variety_code", "06011"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsFrontendOrigins() throws Exception {
        mockMvc.perform(options("/user/google")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mockMvc.perform(options("/storage")
                        .header(HttpHeaders.ORIGIN, "https://hipapple-front.pages.dev")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://hipapple-front.pages.dev"));
    }

    @Test
    void swaggerDocumentsSpecificationEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/user/google'].post").exists())
                .andExpect(jsonPath("$.paths['/user/profile'].post").exists())
                .andExpect(jsonPath("$.paths['/user/login']").doesNotExist())
                .andExpect(jsonPath("$.paths['/user/me'].get").exists())
                .andExpect(jsonPath("$.paths['/storage'].post").exists())
                .andExpect(jsonPath("$.paths['/storage'].get").exists())
                .andExpect(jsonPath("$.paths['/storage/{storageId}'].get").exists())
                .andExpect(jsonPath("$.paths['/storage/{storageId}'].put").exists())
                .andExpect(jsonPath("$.paths['/storage/{storageId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/storage/{storageId}/quality-check'].post").exists())
                .andExpect(jsonPath("$.paths['/storage/{storageId}/quality-classify'].post").exists())
                .andExpect(jsonPath("$.paths['/api/price/dashboard'].get").exists());
    }
}
