package com.margomarket.listing.comment;

public record CommentSendResponse(ListingCommentResponse comment, boolean censored,
                                  CommentPostingStatus postingStatus) {}
