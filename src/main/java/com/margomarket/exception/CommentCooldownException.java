package com.margomarket.exception;

import lombok.Getter;
import com.margomarket.dto.CommentPostingStatus;

@Getter
public class CommentCooldownException extends RuntimeException {
    private final CommentPostingStatus postingStatus;

    public CommentCooldownException(CommentPostingStatus postingStatus) {
        super("W tej dyskusji możesz wysłać jedną wiadomość co 10 minut.");
        this.postingStatus = postingStatus;
    }

    public int getRetryAfterSeconds() { return postingStatus.retryAfterSeconds(); }
}
