package com.mathbot.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorDetail error;
    private final String timestamp;

    private ApiResponse(boolean success, T data, ErrorDetail error) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.timestamp = OffsetDateTime.now().toString();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message, null));
    }

    public static <T> ApiResponse<T> validationError(Map<String, String> fieldErrors) {
        ErrorDetail detail = new ErrorDetail("VALIDATION_ERROR",
                "Validation failed", fieldErrors);
        return new ApiResponse<>(false, null, detail);
    }

    @Getter
    public static class ErrorDetail {
        private final String code;
        private final String message;
        private final Map<String, String> fieldErrors;

        public ErrorDetail(String code, String message, Map<String, String> fieldErrors) {
            this.code = code;
            this.message = message;
            this.fieldErrors = fieldErrors;
        }
    }
}
