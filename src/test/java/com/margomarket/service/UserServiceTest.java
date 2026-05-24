package com.margomarket.service;

import com.margomarket.dto.RegisterRequest;
import com.margomarket.dto.UserStats;
import com.margomarket.exception.EmailAlreadyUsedException;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingStatus;
import com.margomarket.model.Role;
import com.margomarket.model.User;
import com.margomarket.repository.ListingRepository;
import com.margomarket.repository.RoleRepository;
import com.margomarket.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerUserNormalizesEmailEncodesPasswordAndAssignsUserRole() {
        Role role = new Role("user");
        RegisterRequest request = new RegisterRequest("  TEST@Example.COM  ", "secret123");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByName("user")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User registered = userService.registerUser(request);

        assertThat(registered.getEmail()).isEqualTo("test@example.com");
        assertThat(registered.getPassword()).isEqualTo("encoded-password");
        assertThat(registered.getRole()).isSameAs(role);
        verify(userRepository).save(registered);
    }

    @Test
    void registerUserThrowsWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("test@example.com", "secret123");
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verifyNoInteractions(roleRepository, passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUserThrowsWhenDefaultRoleIsMissing() {
        RegisterRequest request = new RegisterRequest("test@example.com", "secret123");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByName("user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserStatsCountsTotalActiveAndSoldListings() {
        User user = user(1L, "user");
        Listing active = listingWithStatus("active");
        Listing sold = listingWithStatus("sold");
        Listing inactive = listingWithStatus("inactive");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(listingRepository.findByUserSortedByStatusAndCreatedAtDesc(user))
                .thenReturn(List.of(active, sold, inactive));

        UserStats stats = userService.getUserStats(1L);

        assertThat(stats.getTotalListings()).isEqualTo(3);
        assertThat(stats.getActiveListings()).isEqualTo(1);
        assertThat(stats.getSoldListings()).isEqualTo(1);
    }

    @Test
    void deleteUserRejectsDeletingOwnAccount() {
        User admin = user(1L, "admin");

        assertThatThrownBy(() -> userService.deleteUser(1L, admin))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUserRejectsDeletingLastAdmin() {
        User currentAdmin = user(1L, "admin");
        User targetAdmin = user(2L, "admin");

        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.countAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteUser(2L, currentAdmin))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUserDeletesExistingNonSelfUser() {
        User currentAdmin = user(1L, "admin");
        User targetUser = user(2L, "user");

        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

        userService.deleteUser(2L, currentAdmin);

        verify(userRepository).delete(targetUser);
    }

    private static User user(Long id, String roleName) {
        User user = new User();
        user.setId(id);
        user.setRole(new Role(roleName));
        return user;
    }

    private static Listing listingWithStatus(String statusName) {
        ListingStatus status = new ListingStatus();
        status.setName(statusName);

        Listing listing = new Listing();
        listing.setStatus(status);
        return listing;
    }
}
