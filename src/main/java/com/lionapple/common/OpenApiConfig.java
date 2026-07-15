package com.lionapple.common;

import com.lionapple.common.auth.CurrentUserId;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI lionAppleOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lion Apple API")
                        .description("API 명세서 기반 사과 저장고 백엔드")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
