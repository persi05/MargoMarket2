package com.margomarket.user.internal;

import com.margomarket.shared.ApiError;
import com.margomarket.shared.GlobalExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Account data conflicts. */
@RestControllerAdvice
@Order(GlobalExceptionHandler.MODULE_HANDLERS)
class UserExceptionHandler {
    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> conflict(EmailAlreadyUsedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", exception.getMessage()));
    }
}
