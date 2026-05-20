package com.margomarket.controller;

import com.margomarket.dto.ListingFilter;
import com.margomarket.dto.ListingResponse;
import com.margomarket.dto.PageResponse;
import com.margomarket.dto.UserResponse;
import com.margomarket.dto.UserStats;
import com.margomarket.mapper.ListingMapper;
import com.margomarket.mapper.PageMapper;
import com.margomarket.mapper.UserMapper;
import com.margomarket.model.User;
import com.margomarket.service.ListingService;
import com.margomarket.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ListingService listingService;
    private final UserService userService;
    private final ListingMapper listingMapper;
    private final UserMapper userMapper;
    private final PageMapper pageMapper;

    @GetMapping("/listings")
    public PageResponse<ListingResponse> listings(@ModelAttribute ListingFilter filter) {
        return pageMapper.toResponse(listingService.searchAllListingsAdmin(filter), listingMapper::toResponse);
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return userService.getAllUsers().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @GetMapping("/users/{id}")
    public UserResponse user(@PathVariable Long id) {
        return userMapper.toResponse(userService.getById(id));
    }

    @GetMapping("/users/{id}/stats")
    public UserStats userStats(@PathVariable Long id) {
        return userService.getUserStats(id);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        userService.deleteUser(id, currentUser);
    }
}
