package com.lionapple;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSpecificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void userApisMatchSpecification() throws Exception {
        mockMvc.perform(post("/user/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        mockMvc.perform(post("/user/profile")
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

        mockMvc.perform(get("/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("부사 농가"));
    }

    @Test
    void storageApisMatchSpecification() throws Exception {
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Success"));

        mockMvc.perform(get("/storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].storageId").exists())
                .andExpect(jsonPath("$[0].startDate").exists());

        mockMvc.perform(get("/storage/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").exists())
                .andExpect(jsonPath("$.humidity").exists())
                .andExpect(jsonPath("$.ethylene").exists());

        mockMvc.perform(put("/storage/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Success"));

        mockMvc.perform(delete("/storage/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("삭제 완료"));
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
                .andExpect(jsonPath("$.paths['/storage/{storageId}'].delete").exists());
    }
}
