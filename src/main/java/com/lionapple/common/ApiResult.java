package com.lionapple.common;

public record ApiResult(String result) {

    public static ApiResult success() {
        return new ApiResult("Success");
    }

    public static ApiResult deleted() {
        return new ApiResult("삭제 완료");
    }
}
