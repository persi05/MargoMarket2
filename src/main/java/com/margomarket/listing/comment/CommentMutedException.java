package com.margomarket.listing.comment;

import lombok.Getter;

@Getter
public class CommentMutedException extends RuntimeException {
    private final CommentPostingStatus postingStatus;

    public CommentMutedException(CommentPostingStatus postingStatus) {
        super("Za obraźliwą komunikację nałożono wyciszenie na 12 godzin we wszystkich dyskusjach.");
        this.postingStatus = postingStatus;
    }

    public int getRetryAfterSeconds() { return postingStatus.retryAfterSeconds(); }
}
