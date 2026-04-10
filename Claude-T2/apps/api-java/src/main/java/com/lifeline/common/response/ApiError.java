package com.lifeline.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private int code;
    private String message;
    private Object data;
    private Object errors;
    private String timestamp;
    private String path;

    public static ApiError of(int code, String message, String path) {
        return new ApiError(code, message, null, null, LocalDateTime.now().toString(), path);
    }

    public static ApiError of(int code, String message, Object errors, String path) {
        return new ApiError(code, message, null, errors, LocalDateTime.now().toString(), path);
    }
}
