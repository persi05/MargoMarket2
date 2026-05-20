package com.margomarket.controller;

import com.margomarket.dto.LoginRequest;
import com.margomarket.dto.LoginResponse;
import com.margomarket.dto.RegisterRequest;
import com.margomarket.dto.UserResponse;
import com.margomarket.mapper.UserMapper;
import com.margomarket.model.User;
import com.margomarket.security.JwtService;
import com.margomarket.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SecurityController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userService.getByEmail(request.email());
        return new LoginResponse(
                "Bearer",
                jwtService.generateToken(user),
                jwtService.getExpirationMillis(),
                userMapper.toResponse(user)
        );
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userMapper.toResponse(userService.registerUser(request));
    }

    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal User user) {
        return userMapper.toResponse(user);
    }
}
