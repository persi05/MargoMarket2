package com.margomarket.service;

import com.margomarket.dto.SellerReviewDtos.CreateReviewRequest;
import com.margomarket.model.User;
import com.margomarket.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerReviewServiceTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UserModerationService moderation = mock(UserModerationService.class);
    SellerReviewService service = new SellerReviewService(jdbc, mock(UserRepository.class), moderation,
            mock(TextModerationService.class), mock(NotificationService.class));

    @Test
    void summarisesRatings() {
        var summary = SellerReviewService.summary(new long[]{1, 0, 1, 0, 3});
        assertThat(summary.count()).isEqualTo(5);
        assertThat(summary.average()).isEqualTo(3.8);
        assertThat(summary.positivePercent()).isEqualTo(60);
        assertThat(summary.distribution()).containsExactly(1L, 0L, 1L, 0L, 3L);
    }

    @Test
    void emptySummaryHasNoAverage() {
        var summary = SellerReviewService.summary(new long[5]);
        assertThat(summary.count()).isZero();
        assertThat(summary.average()).isNull();
        assertThat(summary.positivePercent()).isNull();
    }

    @Test
    void rejectsInvalidInputBeforeTouchingTheDatabase() {
        User buyer = new User(); buyer.setId(1L);
        assertThatThrownBy(() -> service.create(buyer, new CreateReviewRequest(null, 5, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(buyer, new CreateReviewRequest(1L, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(buyer, new CreateReviewRequest(1L, 3, "a".repeat(1001)))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
        verify(moderation, times(3)).requireAllowed(buyer, ModerationScope.REVIEWS);
    }
}
