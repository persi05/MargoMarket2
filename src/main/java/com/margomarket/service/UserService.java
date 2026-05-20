package com.margomarket.service;

import com.margomarket.dto.RegisterRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ListingRepository listingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Użytkownik nie istnieje: " + email));
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
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException("Ten email jest już zarejestrowany");
        }

        Role role = roleRepository.findByName("user")
                .orElseThrow(() -> new NotFoundException("Brakuje roli user"));

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);
        return userRepository.save(user);
    }

    public UserStats getUserStats(Long userId) {
        User user = getById(userId);

        var userListings = listingRepository.findByUserOrderByCreatedAtDesc(user);
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
