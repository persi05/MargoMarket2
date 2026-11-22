package com.margomarket.admin;

import com.margomarket.listing.ListingFilter;
import com.margomarket.listing.ListingMapper;
import com.margomarket.listing.ListingResponse;
import com.margomarket.listing.ListingService;
import com.margomarket.shared.PageMapper;
import com.margomarket.shared.PageResponse;
import com.margomarket.user.User;
import com.margomarket.user.UserMapper;
import com.margomarket.user.UserResponse;
import com.margomarket.user.UserService;
import com.margomarket.listing.UserListingStats;
import com.margomarket.listing.UserStats;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Administracja", description = "Przegląd użytkowników i ogłoszeń")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ListingService listingService;
    private final UserService userService;
    private final UserListingStats listingStats;
    private final ListingMapper listingMapper;
    private final UserMapper userMapper;
    private final PageMapper pageMapper;

    @GetMapping("/listings")
    public PageResponse<ListingResponse> listings(@ModelAttribute ListingFilter filter) {
        return pageMapper.toResponse(listingService.searchAllListingsAdmin(filter), listingMapper::toResponse);
    }

    @GetMapping("/users")
    public PageResponse<AdminUserRowResponse> users(@RequestParam(defaultValue = "1") int page) {
        Page<User> users = userService.getAllUsers(page);
        Map<Long, UserStats> stats = listingStats.forUsers(
                users.getContent().stream().map(User::getId).toList());
        return pageMapper.toResponse(users, user -> new AdminUserRowResponse(
                userMapper.toResponse(user),
                stats.getOrDefault(user.getId(), new UserStats(0, 0, 0))));
    }

    @GetMapping("/users/{id}")
    public UserResponse user(@PathVariable Long id) {
        return userMapper.toResponse(userService.getById(id));
    }

    @GetMapping("/users/{id}/stats")
    public UserStats userStats(@PathVariable Long id) {
        userService.getById(id);
        return listingStats.forUser(id);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        userService.deleteUser(id, currentUser);
    }
}
