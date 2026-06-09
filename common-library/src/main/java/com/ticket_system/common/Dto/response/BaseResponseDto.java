package com.ticket_system.common.Dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponseDto<T> {

    private int status;
    private String message;
    private T data;
    private Long timestamp;

    // ✅ Success response (có data)
    public static <T> BaseResponseDto<T> success(int status, String message, T data) {
        return BaseResponseDto.<T>builder()
                .status(status)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    // ❌ Error response
    public static <T> BaseResponseDto<T> error(int status, String message) {
        return BaseResponseDto.<T>builder()
                .status(status)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
