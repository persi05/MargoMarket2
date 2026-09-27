package com.margomarket.service;

import com.margomarket.dto.ListingCommentResponse;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingComment;
import com.margomarket.model.ListingStatus;
import com.margomarket.model.Role;
import com.margomarket.model.User;
import com.margomarket.repository.ListingCommentRepository;
import com.margomarket.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingCommentServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ListingCommentRepository commentRepository;

    @Mock
    private TextModerationService moderation;

    @Mock
    private DiscussionCooldownService cooldown;

    @InjectMocks
    private ListingCommentService commentService;

    @Test
    void addCommentTrimsBodyAndMarksSeller() {
        when(moderation.masker()).thenReturn(java.util.function.UnaryOperator.identity());
        User seller = user(4L);
        Listing listing = listing("active", seller);
        when(listingRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(listing));
        when(commentRepository.save(any(ListingComment.class))).thenAnswer(invocation -> {
            ListingComment comment = invocation.getArgument(0);
            comment.setId(18L);
            comment.setCreatedAt(LocalDateTime.now());
            return comment;
        });

        ListingCommentResponse response = commentService.addComment(12L, "  Czy aktualne?  ", seller);

        assertThat(response.id()).isEqualTo(18L);
        assertThat(response.body()).isEqualTo("Czy aktualne?");
        assertThat(response.seller()).isTrue();
        assertThat(response.canDelete()).isTrue();
        verify(commentRepository).save(any(ListingComment.class));
        verify(cooldown).claim(12L, seller.getId(), false);
    }

    @Test
    void oldCommentsAreMaskedBeforeReturningAndOriginalBodyIsPreserved() {
        Listing listing = listing("active", user(4L));
        ListingComment comment = comment(user(5L), LocalDateTime.now().minusDays(3));
        comment.setBody("zakazane słowo");
        when(listingRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(listing));
        when(commentRepository.findByListingIdOrderByIdDesc(org.mockito.ArgumentMatchers.eq(12L), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(comment)));
        when(moderation.masker()).thenReturn(body -> "******** słowo");

        var result = commentService.getComments(12L, 1, null);

        assertThat(result.getContent().getFirst().body()).isEqualTo("******** słowo");
        assertThat(comment.getBody()).isEqualTo("zakazane słowo");
        assertThat(result.getContent().getFirst().canDelete()).isFalse();
    }

    @Test
    void newlyAddedCommentIsMaskedInResponse() {
        when(listingRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(listing("active", user(4L))));
        when(commentRepository.save(any(ListingComment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moderation.masker()).thenReturn(body -> "****");
        assertThat(commentService.addComment(12L, "test", user(5L)).body()).isEqualTo("****");
        verify(commentRepository).save(org.mockito.ArgumentMatchers.argThat(comment -> comment.getBody().equals("****")));
        verify(cooldown).claim(12L, 5L, true);
    }

    @Test
    void administratorCanSendRepeatedCensoredMessagesWithoutAnyLimits() {
        when(listingRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(listing("active", user(4L))));
        when(moderation.masker()).thenReturn(body -> "****");
        when(commentRepository.save(any(ListingComment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User admin = user(5L);
        admin.setRole(new Role("admin"));
        assertThat(commentService.addComment(12L, "test", admin).body()).isEqualTo("****");
        assertThat(commentService.addComment(12L, "test", admin).body()).isEqualTo("****");
        org.mockito.Mockito.verifyNoInteractions(cooldown);
    }

    @Test
    void cooldownRejectsRepeatSendBeforeSavingComment() {
        when(listingRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(listing("active", user(4L))));
        when(moderation.masker()).thenReturn(java.util.function.UnaryOperator.identity());
        org.mockito.Mockito.doThrow(new com.margomarket.exception.CommentCooldownException(status(599, 0)))
                .when(cooldown).claim(12L, 5L, false);

        assertThatThrownBy(() -> commentService.addComment(12L, "hello", user(5L)))
                .isInstanceOf(com.margomarket.exception.CommentCooldownException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void postingStatusUsesAuthenticatedAuthorAndDiscussion() {
        when(listingRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(listing("active", user(4L))));
        when(cooldown.status(12L, 5L)).thenReturn(status(12, 0));
        assertThat(commentService.postingStatus(12L, user(5L)).retryAfterSeconds()).isEqualTo(12);
    }

    @Test
    void mutedAuthorCannotSendEvenInAnotherDiscussion() {
        when(listingRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(listing("active", user(4L))));
        when(moderation.masker()).thenReturn(java.util.function.UnaryOperator.identity());
        org.mockito.Mockito.doThrow(new com.margomarket.exception.CommentMutedException(status(43000, 43000)))
                .when(cooldown).claim(12L, 5L, false);
        assertThatThrownBy(() -> commentService.addComment(12L, "hello", user(5L)))
                .isInstanceOf(com.margomarket.exception.CommentMutedException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void administratorStatusIgnoresPreviouslyStoredMuteAndCooldown() {
        when(listingRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(listing("active", user(4L))));
        when(cooldown.unrestrictedStatus()).thenReturn(status(0, 0));
        User admin = user(5L);
        admin.setRole(new Role("admin"));
        var result = commentService.postingStatus(12L, admin);
        assertThat(result.retryAfterSeconds()).isZero();
        assertThat(result.mutedUntil()).isNull();
        verify(cooldown, never()).status(any(), any());
    }

    private static com.margomarket.dto.CommentPostingStatus status(int retrySeconds, int muteSeconds) {
        var now = java.time.Instant.parse("2026-09-27T18:00:00Z");
        return new com.margomarket.dto.CommentPostingStatus(retrySeconds, muteSeconds, now,
                muteSeconds > 0 ? now.plusSeconds(muteSeconds) : null);
    }

    @Test
    void addCommentRejectsSoldListing() {
        Listing listing = listing("sold", user(4L));
        when(listingRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> commentService.addComment(12L, "Wiadomość", user(5L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void authorCanDeleteRecentComment() {
        User author = user(4L);
        ListingComment comment = comment(author, LocalDateTime.now().minusMinutes(14));
        when(commentRepository.findById(18L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(12L, 18L, author);

        verify(commentRepository).delete(comment);
    }

    @Test
    void authorCannotDeleteCommentAfterFifteenMinutes() {
        User author = user(4L);
        ListingComment comment = comment(author, LocalDateTime.now().minusMinutes(16));
        when(commentRepository.findById(18L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(12L, 18L, author))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void adminCanDeleteOldComment() {
        ListingComment comment = comment(user(4L), LocalDateTime.now().minusDays(2));
        when(commentRepository.findById(18L)).thenReturn(Optional.of(comment));
        User admin = user(5L);
        admin.setRole(new Role("admin"));

        commentService.deleteComment(12L, 18L, admin);

        verify(commentRepository).delete(comment);
    }

    @Test
    void otherUserCannotDeleteComment() {
        ListingComment comment = comment(user(4L), LocalDateTime.now());
        when(commentRepository.findById(18L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(12L, 18L, user(5L)))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(commentRepository, never()).delete(any());
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(new Role("user"));
        return user;
    }

    private static ListingComment comment(User author, LocalDateTime createdAt) {
        ListingComment comment = new ListingComment();
        comment.setId(18L);
        comment.setListing(listing("active", author));
        comment.setAuthor(author);
        comment.setCreatedAt(createdAt);
        return comment;
    }

    private static Listing listing(String statusName, User seller) {
        ListingStatus status = new ListingStatus();
        status.setName(statusName);
        Listing listing = new Listing();
        listing.setId(12L);
        listing.setUser(seller);
        listing.setStatus(status);
        return listing;
    }
}
