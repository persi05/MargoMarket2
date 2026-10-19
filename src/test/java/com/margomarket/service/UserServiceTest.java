package com.margomarket.service;

import com.margomarket.dto.RegisterRequest;
import com.margomarket.dto.ChangeDisplayNameRequest;
import com.margomarket.dto.ChangePasswordRequest;
import com.margomarket.dto.VerifyEmailRequest;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TextModerationService moderation;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void configureMail() {
        ReflectionTestUtils.setField(userService, "mailFrom", "no-reply@example.com");
        ReflectionTestUtils.setField(userService, "blockedDomains", "10minutemail.com,mailinator.com");
        ReflectionTestUtils.setField(userService, "codeExpirationMinutes", 10);
        ReflectionTestUtils.setField(userService, "resendCooldownSeconds", 60);
        ReflectionTestUtils.setField(userService, "maxVerificationAttempts", 5);
    }

    @Test
    void registerUserNormalizesEmailEncodesPasswordAndAssignsUserRole() {
        ReflectionTestUtils.setField(userService, "codeExpirationMinutes", 3);
        Role role = new Role("user");
        RegisterRequest request = new RegisterRequest("Alice", "Alicja", "  TEST@Example.COM  ", "secret123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("user")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User registered = userService.registerUser(request);

        assertThat(registered.getEmail()).isEqualTo("test@example.com");
        assertThat(registered.getUsername()).isEqualTo("alice");
        assertThat(registered.getDisplayName()).isEqualTo("Alicja");
        assertThat(registered.isEmailVerified()).isFalse();
        assertThat(registered.getVerificationExpiresAt())
                .isBetween(java.time.LocalDateTime.now().plusMinutes(2), java.time.LocalDateTime.now().plusMinutes(4));
        assertThat(registered.getPassword()).isEqualTo("encoded-password");
        assertThat(registered.getRole()).isSameAs(role);
        verify(userRepository).saveAndFlush(registered);
        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void registerUserThrowsWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("alice", "Alicja", "test@example.com", "secret123");
        User existing = new User();
        existing.setEmailVerified(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verifyNoInteractions(roleRepository, passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerUserThrowsWhenDefaultRoleIsMissing() {
        RegisterRequest request = new RegisterRequest("alice", "Alicja", "test@example.com", "secret123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerUserRejectsDisposableDomainAndSubdomain() {
        RegisterRequest request = new RegisterRequest("alice", "Alicja", "a@sub.mailinator.com", "secret123");
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, roleRepository, mailSender);
    }

    @Test
    void registerUserRejectsBlockedDisplayNameBeforeSavingOrSendingEmail() {
        when(moderation.containsBlockedWord("Zły Pseudonim")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(
                new RegisterRequest("alice", "Zły Pseudonim", "alice@example.com", "secret123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("niedozwolone słowo");
        verifyNoInteractions(userRepository, mailSender);
    }

    @Test
    void registerUserUsesConfiguredResendCooldown() {
        ReflectionTestUtils.setField(userService, "resendCooldownSeconds", 120);
        User pending = new User();
        pending.setEmailVerified(false);
        pending.setVerificationSentAt(java.time.LocalDateTime.now().minusSeconds(90));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> userService.registerUser(
                new RegisterRequest("alice", "Alicja", "test@example.com", "secret123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nowy kod można wysłać za");
        verifyNoInteractions(mailSender);
    }

    @Test
    void registerUserReleasesExpiredPendingUsernameBeforeCheckingAvailability() {
        RegisterRequest request = new RegisterRequest("alice", "Alicja", "new@example.com", "secret123");
        AtomicBoolean usernameReserved = new AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            usernameReserved.set(false);
            return 1;
        }).when(userRepository).deleteExpiredUnverifiedMatching(any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq("new@example.com"), org.mockito.ArgumentMatchers.eq("alice"));
        when(userRepository.existsByUsername("alice")).thenAnswer(invocation -> usernameReserved.get());
        when(roleRepository.findByName("user")).thenReturn(Optional.of(new Role("user")));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User registered = userService.registerUser(request);

        assertThat(usernameReserved.get()).isFalse();
        assertThat(registered.getUsername()).isEqualTo("alice");
        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void verifyEmailUsesConfiguredAttemptLimit() {
        ReflectionTestUtils.setField(userService, "maxVerificationAttempts", 1);
        User pending = new User();
        pending.setEmailVerified(false);
        pending.setVerificationCodeHash("hash");
        pending.setVerificationExpiresAt(java.time.LocalDateTime.now().plusMinutes(5));
        pending.setVerificationAttempts(1);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(pending));

        assertThat(userService.verifyEmail(new VerifyEmailRequest("a@example.com", "123456"))).isFalse();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void verifyEmailRejectsWrongCodeAndCountsAttempt() {
        User pending = new User();
        pending.setEmailVerified(false);
        pending.setVerificationCodeHash("hash");
        pending.setVerificationExpiresAt(java.time.LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(false);

        assertThat(userService.verifyEmail(new VerifyEmailRequest("a@example.com", "123456"))).isFalse();
        assertThat(pending.getVerificationAttempts()).isEqualTo(1);
        assertThat(pending.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmailActivatesAccountAndClearsCode() {
        User pending = new User();
        pending.setEmailVerified(false);
        pending.setVerificationCodeHash("hash");
        pending.setVerificationExpiresAt(java.time.LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        assertThat(userService.verifyEmail(new VerifyEmailRequest("a@example.com", "123456"))).isTrue();
        assertThat(pending.isEmailVerified()).isTrue();
        assertThat(pending.getVerificationCodeHash()).isNull();
    }

    @Test
    void changeDisplayNameRequiresCurrentPassword() {
        User user = user(1L, "user");
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setPassword("hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeDisplayName(1L, new ChangeDisplayNameRequest("Bob", "wrong")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getDisplayName()).isEqualTo("Alice");
    }

    @Test
    void changeDisplayNameKeepsLoginAndEmail() {
        User user = user(1L, "user");
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setEmail("alice@example.com");
        user.setPassword("hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        User updated = userService.changeDisplayName(1L, new ChangeDisplayNameRequest("  Bob  ", "secret"));

        assertThat(updated.getDisplayName()).isEqualTo("Bob");
        assertThat(updated.getUsername()).isEqualTo("alice");
        assertThat(updated.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void changeDisplayNameRejectsTooShortNameAfterTrimming() {
        User user = user(1L, "user");
        user.setDisplayName("Alice");
        user.setPassword("hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> userService.changeDisplayName(1L, new ChangeDisplayNameRequest(" a ", "secret")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getDisplayName()).isEqualTo("Alice");
    }

    @Test
    void changeDisplayNameRejectsBlockedWordWithoutChangingExistingName() {
        User user = user(1L, "user");
        user.setDisplayName("Alice");
        user.setPassword("hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(moderation.containsBlockedWord("Zły Pseudonim")).thenReturn(true);

        assertThatThrownBy(() -> userService.changeDisplayName(1L,
                new ChangeDisplayNameRequest("Zły Pseudonim", "secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("niedozwolone słowo");
        assertThat(user.getDisplayName()).isEqualTo("Alice");
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        User user = user(1L, "user");
        user.setPassword("old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword(1L, new ChangePasswordRequest("wrong", "newsecret")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getPassword()).isEqualTo("old-hash");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void changePasswordStoresEncodedNewPassword() {
        User user = user(1L, "user");
        user.setPassword("old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldsecret", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newsecret")).thenReturn("new-hash");

        userService.changePassword(1L, new ChangePasswordRequest("oldsecret", "newsecret"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void getUserStatsCountsTotalActiveAndSoldListings() {
        User user = user(1L, "user");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(listingRepository.countUserListingsByStatus(1L))
                .thenReturn(java.util.Collections.singletonList(new Object[] {3L, 1L, 1L}));

        UserStats stats = userService.getUserStats(1L);

        assertThat(stats.getTotalListings()).isEqualTo(3);
        assertThat(stats.getActiveListings()).isEqualTo(1);
        assertThat(stats.getSoldListings()).isEqualTo(1);
    }

    @Test
    void getUserStatsForUsersUsesOneGroupedQueryForThePage() {
        List<Long> userIds = List.of(1L, 2L, 3L);
        when(listingRepository.countUserListingsByStatusForUsers(userIds))
                .thenReturn(List.<Object[]>of(
                        new Object[] {1L, 4L, 2L, 1L},
                        new Object[] {3L, 1L, 1L, 0L}
                ));

        var stats = userService.getUserStatsForUsers(userIds);

        assertThat(stats).containsOnlyKeys(1L, 3L);
        assertThat(stats.get(1L).getTotalListings()).isEqualTo(4);
        assertThat(stats.get(1L).getActiveListings()).isEqualTo(2);
        assertThat(stats.get(3L).getSoldListings()).isZero();
        verify(listingRepository).countUserListingsByStatusForUsers(userIds);
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
