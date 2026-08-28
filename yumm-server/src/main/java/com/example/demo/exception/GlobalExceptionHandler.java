package com.example.demo.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(CustomException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", e.getErrorCode().name());
        body.put("message", e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "VALIDATION_FAILED");
        body.put("message", e.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 요청 본문 파싱 실패. enum 밖의 값(예: region="강남구")이 여기서 걸린다.
     * 아래 RuntimeException 핸들러가 먼저 잡으면 500이 나가므로 명시적으로 400으로 내린다.
     * Jackson 원문 메시지는 내부 타입명을 흘리므로 노출하지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ErrorCode.VALIDATION_ERROR.name());
        body.put("message", ErrorCode.VALIDATION_ERROR.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "RUNTIME_EXCEPTION");
        body.put("message", e.getMessage());
        return ResponseEntity.internalServerError().body(body);
    }
}
