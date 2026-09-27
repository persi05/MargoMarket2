package com.margomarket.dto;

public record CommentSendResponse(ListingCommentResponse comment, boolean censored,
                                  CommentPostingStatus postingStatus) {}
