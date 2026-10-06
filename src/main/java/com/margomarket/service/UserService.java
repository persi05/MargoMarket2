package com.margomarket.service;

import com.margomarket.dto.RegisterRequest;
import com.margomarket.dto.VerifyEmailRequest;
import com.margomarket.dto.UserStats;
import com.margomarket.exception.EmailAlreadyUsedException;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Role;
import com.margomarket.model.User;
import com.margomarket.repository.ListingRepository;
import com.margomarket.repository.RoleRepository;
import com.margomarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.time.Duration;
import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ListingRepository listingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.registration.blocked-domains}")
    private String blockedDomains;

    @Value("${app.registration.code-expiration-minutes}")
    private int codeExpirationMinutes;

    @Value("${app.registration.resend-cooldown-seconds}")
    private int resendCooldownSeconds;

    @Value("${app.registration.max-verification-attempts}")
    private int maxVerificationAttempts;

    @PostConstruct
    void validateRegistrationSettings() {
        if (codeExpirationMinutes <= 0 || resendCooldownSeconds < 0 || maxVerificationAttempts <= 0) {
            throw new IllegalStateException("Niepoprawna konfiguracja limitów rejestracji");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Użytkownik nie istnieje"));
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Użytkownik nie istnieje"));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Użytkownik nie istnieje"));
    }

    @Transactional
    public User registerUser(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String domain = email.substring(email.lastIndexOf('@') + 1);
        Set<String> blocked = Set.copyOf(Arrays.stream(blockedDomains.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).filter(value -> !value.isEmpty()).toList());
        if (blocked.stream().anyMatch(value -> domain.equals(value) || domain.endsWith("." + value))) {
            throw new IllegalArgumentException("Adresy z tymczasowych skrzynek są niedozwolone");
        }

        userRepository.deleteExpiredUnverifiedMatching(LocalDateTime.now(), email, username);
        User user = userRepository.findByEmail(email).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (user != null && user.isEmailVerified()) {
            throw new EmailAlreadyUsedException("Ten email jest już zarejestrowany");
        }
        if (user != null && user.getVerificationSentAt() != null
                && user.getVerificationSentAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            long remainingMillis = Duration.between(now,
                    user.getVerificationSentAt().plusSeconds(resendCooldownSeconds)).toMillis();
            long remainingSeconds = (remainingMillis + 999) / 1000;
            throw new IllegalArgumentException("Nowy kod można wysłać za " + remainingSeconds + " s");
        }
        if (userRepository.existsByUsername(username) && (user == null || !user.getUsername().equals(username))) {
            throw new EmailAlreadyUsedException("Ta nazwa użytkownika jest już zajęta");
        }

        Role role = roleRepository.findByName("user")
                .orElseThrow(() -> new NotFoundException("Brakuje roli user"));

        if (user == null) user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setEmailVerified(false);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        user.setVerificationCodeHash(passwordEncoder.encode(code));
        user.setVerificationExpiresAt(now.plusMinutes(codeExpirationMinutes));
        user.setVerificationSentAt(now);
        user.setVerificationAttempts(0);
        User saved = userRepository.saveAndFlush(user);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("MargoMarket — kod potwierdzający");
        message.setText("Twój kod potwierdzający: " + code + "\nKod jest ważny przez "
                + codeExpirationMinutes + " minut. Jeśli nie zakładasz konta, zignoruj tę wiadomość.");
        mailSender.send(message);
        return saved;
    }

    @Scheduled(fixedDelayString = "${app.registration.cleanup-interval-ms}")
    @Transactional
    public void deleteExpiredUnverifiedUsers() {
        userRepository.deleteExpiredUnverified(LocalDateTime.now());
    }

    @Transactional
    public boolean verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null || user.isEmailVerified() || user.getVerificationCodeHash() == null) return false;
        if (!user.getVerificationExpiresAt().isAfter(LocalDateTime.now())
                || user.getVerificationAttempts() >= maxVerificationAttempts) return false;
        if (!passwordEncoder.matches(request.code(), user.getVerificationCodeHash())) {
            user.setVerificationAttempts(user.getVerificationAttempts() + 1);
            return false;
        }
        user.setEmailVerified(true);
        user.setVerificationCodeHash(null);
        user.setVerificationExpiresAt(null);
        user.setVerificationSentAt(null);
        user.setVerificationAttempts(0);
        return true;
    }

    public UserStats getUserStats(Long userId) {
        User user = getById(userId);

        var userListings = listingRepository.findByUserSortedByStatusAndCreatedAtDesc(user);
        long total = userListings.size();
        long active = userListings.stream().filter(listing -> "active".equals(listing.getStatus().getName())).count();
        long sold = userListings.stream().filter(listing -> "sold".equals(listing.getStatus().getName())).count();

        return new UserStats(total, active, sold);
    }

    @Transactional
    public void deleteUser(Long userId, User currentUser) {
        if (userId.equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Nie można usunąć własnego konta administratora");
        }

        User user = getById(userId);

        if (user.isAdmin() && userRepository.countAdmins() <= 1) {
            throw new ForbiddenOperationException("Nie można usunąć ostatniego administratora");
        }

        userRepository.delete(user);
    }
}
