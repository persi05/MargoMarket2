package com.margomarket.user;

import com.margomarket.user.internal.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDirectoryTest {
    UserRepository users = mock(UserRepository.class);
    UserDirectory directory = new UserDirectory(users);

    @Test
    void findsOnlyVerifiedAccounts() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, "Gracz", false)));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, "Kupiec", true)));

        assertThat(directory.findVerified(1L)).isEmpty();
        assertThat(directory.findVerified(2L)).map(User::getDisplayName).contains("Kupiec");
        assertThat(directory.findVerified(3L)).isEmpty();
    }

    @Test
    void givesPublicNamesWithoutEmails() {
        when(users.findAllById(Set.of(1L, 2L))).thenReturn(List.of(user(1L, "Kupiec", true), user(2L, "jan@example.com", true)));

        assertThat(directory.publicNames(List.of(1L, 2L, 1L))).isEqualTo(Map.of(1L, "Kupiec", 2L, "Gracz 2"));
    }

    @Test
    void asksForNothingWithoutIds() {
        assertThat(directory.publicNames(List.of())).isEmpty();
        verify(users, never()).findAllById(any());
    }

    private static User user(long id, String displayName, boolean verified) {
        User user = new User();
        user.setId(id);
        user.setDisplayName(displayName);
        user.setEmailVerified(verified);
        return user;
    }
}
