package com.margomarket.exception;

import lombok.Getter;
import com.margomarket.dto.CommentPostingStatus;

@Getter
public class CommentMutedException extends RuntimeException {
    private final CommentPostingStatus postingStatus;

    public CommentMutedException(CommentPostingStatus postingStatus) {
        super("Za obraźliwą komunikację nałożono wyciszenie na 12 godzin we wszystkich dyskusjach.");
        this.postingStatus = postingStatus;
    }

    public int getRetryAfterSeconds() { return postingStatus.retryAfterSeconds(); }
}
