package com.margomarket.user;

import com.margomarket.user.internal.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** What other modules may ask about users. The user tables stay behind this class. */
@Service
@RequiredArgsConstructor
public class UserDirectory {
    private final UserRepository users;

    /** A reference for a relation; the row is not loaded. */
    public User reference(long id) {
        return users.getReferenceById(id);
    }

    /** A user with a verified e-mail, the only kind that has a public profile. */
    public Optional<User> findVerified(long id) {
        return users.findById(id).filter(User::isEmailVerified);
    }

    /** Public names by user id, for showing authors without exposing e-mails. */
    public Map<Long, String> publicNames(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return users.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(User::getId, PublicNames::name, (a, b) -> a));
    }
}
