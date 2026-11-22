package com.margomarket.listing.comment;

import lombok.Getter;

@Getter
public class CommentCooldownException extends RuntimeException {
    private final CommentPostingStatus postingStatus;

    public CommentCooldownException(CommentPostingStatus postingStatus) {
        super("W tej dyskusji możesz wysłać jedną wiadomość co 10 minut.");
        this.postingStatus = postingStatus;
    }

    public int getRetryAfterSeconds() { return postingStatus.retryAfterSeconds(); }
}
