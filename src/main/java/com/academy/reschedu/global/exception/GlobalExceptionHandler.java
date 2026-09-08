package com.academy.reschedu.global.exception;

import com.academy.reschedu.domain.makeup.MakeupTicketLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 컨트롤러마다 복붙돼 있던 IllegalState/IllegalArgument/검증 예외 처리를 한곳으로 모은다.
 * 응답 형식은 기존 컨트롤러들이 쓰던 {"message": "..."} 규약을 그대로 따른다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 발급 제한 초과 시 409로 응답 — 프론트가 alert 대신 confirm을 띄우고 overrideLimit=true로 재요청하도록 유도한다. */
    @ExceptionHandler(MakeupTicketLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleLimitExceededException(MakeupTicketLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage(), "limitExceeded", true));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }
}
