package com.lionapple.price.dto;

import java.util.List;

public record PriceOptionsResponse(
        List<String> markets,
        List<String> varieties
) {
}
