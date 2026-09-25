package com.margomarket.controller;

import com.margomarket.dto.ListingCommentRequest;
import com.margomarket.dto.ListingCommentResponse;
import com.margomarket.dto.PageResponse;
import com.margomarket.mapper.PageMapper;
import com.margomarket.model.User;
import com.margomarket.service.ListingCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings/{listingId}/comments")
@RequiredArgsConstructor
public class ListingCommentController {

    private final ListingCommentService commentService;
    private final PageMapper pageMapper;

    @GetMapping
    public PageResponse<ListingCommentResponse> getComments(
            @PathVariable Long listingId,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal User user
    ) {
        return pageMapper.toResponse(commentService.getComments(listingId, page, user), comment -> comment);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingCommentResponse addComment(
            @PathVariable Long listingId,
            @Valid @RequestBody ListingCommentRequest request,
            @AuthenticationPrincipal User user
    ) {
        return commentService.addComment(listingId, request.body(), user);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable Long listingId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal User user
    ) {
        commentService.deleteComment(listingId, commentId, user);
    }
}
