package com.example.demo.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
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

    /**
     * 경로변수·쿼리파라미터 타입 변환 실패(예: /api/blocks/abc).
     * 아래 RuntimeException 핸들러가 먼저 잡으면 500이 나가므로 명시적으로 400으로 내린다.
     * 변환 실패 원문은 내부 타입명(java.lang.Long 등)을 흘리므로 노출하지 않는다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ErrorCode.VALIDATION_ERROR.name());
        body.put("message", ErrorCode.VALIDATION_ERROR.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 처리하지 못한 예외의 최종 방어선.
     *
     * 예외 원문을 응답에 실으면 DB 제약·컬럼·테이블명 같은 내부 구조가 그대로 나간다.
     * /api/user/signup처럼 인증 없이 부를 수 있는 엔드포인트에서는 아무나 그걸 읽을 수 있으므로
     * 클라이언트에는 일반 문구만 내리고 원인은 서버 로그에만 남긴다(NFR-05).
     * 사용자에게 보여줄 메시지가 있는 오류는 CustomException으로 던지면 위 핸들러가 그대로 내려보낸다.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("처리되지 않은 예외", e);
        Map<String, Object> body = new HashMap<>();
        body.put("error", ErrorCode.INTERNAL_ERROR.name());
        body.put("message", ErrorCode.INTERNAL_ERROR.getMessage());
        return ResponseEntity.internalServerError().body(body);
    }
}
