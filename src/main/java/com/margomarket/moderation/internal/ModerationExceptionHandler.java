package com.margomarket.moderation.internal;

import com.margomarket.moderation.UserRestrictedException;
import com.margomarket.shared.GlobalExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/** Account blocks and per-action restrictions as {@code ACCOUNT_BLOCKED} / {@code ACTION_RESTRICTED}. */
@RestControllerAdvice
@Order(GlobalExceptionHandler.MODULE_HANDLERS)
class ModerationExceptionHandler {
    @ExceptionHandler(UserRestrictedException.class)
    public ResponseEntity<Map<String,Object>> restricted(UserRestrictedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exception.response());
    }
}
