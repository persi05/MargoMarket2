package com.margomarket.service;

import com.margomarket.dto.ListingCommentResponse;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingComment;
import com.margomarket.model.User;
import com.margomarket.repository.ListingCommentRepository;
import com.margomarket.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingCommentService {

    private static final int PAGE_SIZE = 30;
    private static final int DELETE_WINDOW_MINUTES = 15;

    private final ListingRepository listingRepository;
    private final ListingCommentRepository commentRepository;

    public Page<ListingCommentResponse> getComments(Long listingId, int page, User viewer) {
        Listing listing = getListing(listingId);
        return commentRepository.findByListingIdOrderByIdDesc(
                listingId, PageRequest.of(Math.max(0, page - 1), PAGE_SIZE)
        ).map(comment -> toResponse(comment, listing.getUser().getId(), viewer));
    }

    @Transactional
    public ListingCommentResponse addComment(Long listingId, String body, User author) {
        Listing listing = getListing(listingId);
        if (!listing.isActive()) {
            throw new IllegalArgumentException("Dyskusja przy tym ogłoszeniu jest zamknięta.");
        }

        ListingComment comment = new ListingComment();
        comment.setListing(listing);
        comment.setAuthor(author);
        comment.setBody(body.trim());
        return toResponse(commentRepository.save(comment), listing.getUser().getId(), author);
    }

    @Transactional
    public void deleteComment(Long listingId, Long commentId, User user) {
        ListingComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Komentarz nie istnieje"));
        if (!comment.getListing().getId().equals(listingId)) {
            throw new NotFoundException("Komentarz nie istnieje w tym ogłoszeniu");
        }
        if (!canDelete(comment, user)) {
            throw new ForbiddenOperationException("Możesz usunąć swój komentarz przez 15 minut od wysłania.");
        }
        commentRepository.delete(comment);
    }

    private Listing getListing(Long listingId) {
        return listingRepository.findByIdWithDetails(listingId)
                .orElseThrow(() -> new NotFoundException("Ogłoszenie nie istnieje"));
    }

    private ListingCommentResponse toResponse(ListingComment comment, Long sellerId, User viewer) {
        return new ListingCommentResponse(
                comment.getId(),
                comment.getAuthor().getId(),
                sellerId.equals(comment.getAuthor().getId()),
                comment.getBody(),
                comment.getCreatedAt(),
                canDelete(comment, viewer)
        );
    }

    private boolean canDelete(ListingComment comment, User viewer) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isAdmin()) {
            return true;
        }
        return viewer.getId().equals(comment.getAuthor().getId())
                && comment.getCreatedAt() != null
                && !comment.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(DELETE_WINDOW_MINUTES));
    }
}
