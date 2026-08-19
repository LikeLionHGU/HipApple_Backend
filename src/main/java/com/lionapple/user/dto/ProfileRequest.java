package com.lionapple.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "농가 정보 입력 요청")
public record ProfileRequest(
        @Schema(description = "농장 이름", example = "청송 사과농장")
        @NotBlank String farmName,

        @Schema(description = "재배 품종", example = "부사")
        @NotBlank String variety,

        @Schema(description = "농장 규모 (단위는 farmSizeUnit 참조)", example = "10")
        @Positive int farmSize,

        @Schema(description = "농장 규모 단위 (예: 평, 제곱미터, 헥타르), 생략시 평", example = "평")
        String farmSizeUnit,

        @Schema(description = "출하 유형 (예: 직거래, 경매, 도매)", example = "경매")
        @NotBlank String shipmentType,

        @Schema(description = "농장 위치 (시/군/구 단위 권장, 선택 입력)", example = "경북 청송군")
        String farmLocation
) {
}
