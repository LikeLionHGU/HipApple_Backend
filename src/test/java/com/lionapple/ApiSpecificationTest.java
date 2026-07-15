package com.lionapple;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.lionapple.user.GoogleTokenVerifier;
import com.lionapple.user.dto.GoogleUserInfo;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSpecificationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

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
                .andExpect(jsonPath("$.name").value("부사 농가"));
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

        mockMvc.perform(get("/storage")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].storageId").exists())
                .andExpect(jsonPath("$[0].name").value("저장고A"))
                .andExpect(jsonPath("$[0].startDate").exists());

        mockMvc.perform(get("/storage/1")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").exists())
                .andExpect(jsonPath("$.humidity").exists())
                .andExpect(jsonPath("$.ethylene").exists());

        mockMvc.perform(put("/storage/1")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Success"));

        mockMvc.perform(delete("/storage/1")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("삭제 완료"));
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
                .andExpect(jsonPath("$.paths['/api/price/dashboard'].get").exists());
    }
}
