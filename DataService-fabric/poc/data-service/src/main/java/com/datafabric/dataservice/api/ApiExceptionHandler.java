package com.datafabric.dataservice.api;

import com.datafabric.dataservice.exception.CustomerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一 API 错误响应
 *
 * 输出格式遵循 RFC 7807 思路（简化版）：
 *   { "error": "CODE", "message": "...", "timestamp": "..." }
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(CustomerNotFoundException ex) {
        // custId 来自客户端路径参数，回显不算泄漏
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("CUSTOMER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleCubeDown(RestClientException ex) {
        // B4：完整 stacktrace 含 Cube 内部 URL（host:port/query/schema）只入服务端日志
        log.error("Cube REST 调用失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorBody("SEMANTIC_LAYER_UNAVAILABLE",
                        "语义层暂时不可用，请稍后重试"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArg(IllegalArgumentException ex) {
        // controller 自己抛的校验文案，用户可见
        return ResponseEntity.badRequest().body(errorBody("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // B4：未分类异常消息不可控（可能含 driver/SQL/内部路径），只入日志
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("INTERNAL_ERROR", "内部错误，请联系管理员并提供 timestamp"));
    }

    private static Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
