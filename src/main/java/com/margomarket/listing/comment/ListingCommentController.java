package com.margomarket.listing.comment;

import com.margomarket.shared.PageMapper;
import com.margomarket.shared.PageResponse;
import com.margomarket.user.User;

import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Komentarze", description = "Dyskusja pod ogłoszeniami")
@RestController
@RequestMapping("/api/listings/{listingId}/comments")
@RequiredArgsConstructor
public class ListingCommentController {

    private final ListingCommentService commentService;
    private final PageMapper pageMapper;

    @GetMapping("/posting-status")
    public CommentPostingStatus postingStatus(
            @PathVariable Long listingId, @AuthenticationPrincipal User user) {
        return commentService.postingStatus(listingId, user);
    }

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
    public CommentSendResponse addComment(
            @PathVariable Long listingId,
            @Valid @RequestBody ListingCommentRequest request,
            @AuthenticationPrincipal User user
    ) {
        var comment = commentService.addComment(listingId, request.body(), user);
        return new CommentSendResponse(comment,
                !comment.body().equals(request.body().trim()), commentService.postingStatus(listingId, user));
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
