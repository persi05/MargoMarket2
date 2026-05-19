package com.margomarket.margomarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.margomarket.margomarket.dto.UserStats;
import com.margomarket.margomarket.model.Role;
import com.margomarket.margomarket.model.User;
import com.margomarket.margomarket.repository.ListingRepository;
import com.margomarket.margomarket.repository.RoleRepository;
import com.margomarket.margomarket.repository.UserRepository;

import java.util.List;
import java.util.Optional;

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
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public User registerUser(String email, String rawPassword) {
        Role userRole = roleRepository.findByName("user")
                .orElseThrow(() -> new IllegalStateException("Role 'user' not found in database"));

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(userRole);

        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public UserStats getUserStats(Long userId) {
        List<com.margomarket.margomarket.model.Listing> userListings = listingRepository
                .findByUserOrderByCreatedAtDesc(userRepository.getReferenceById(userId));

        long total  = userListings.size();
        long active = userListings.stream().filter(com.margomarket.margomarket.model.Listing::isActive).count();
        long sold   = userListings.stream().filter(com.margomarket.margomarket.model.Listing::isSold).count();

        return new UserStats(total, active, sold);
    }

    @Transactional
    public boolean deleteUser(Long userId, Long currentUserId) {
        if (userId.equals(currentUserId)) {
            return false;
        }
        if (!canDeleteUser(userId)) {
            return false;
        }
        userRepository.deleteById(userId);
        return true;
    }

    public boolean canDeleteUser(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        if (user.isAdmin()) {
            return userRepository.countAdmins() > 1;
        }
        return true;
    }

    public long countUsers() {
        return userRepository.countAllUsers();
    }
}