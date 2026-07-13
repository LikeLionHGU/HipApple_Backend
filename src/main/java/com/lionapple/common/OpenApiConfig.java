package com.lionapple.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lionAppleOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lion Apple API")
                        .description("API 명세서 기반 사과 저장고 백엔드")
                        .version("v1"));
    }
}
