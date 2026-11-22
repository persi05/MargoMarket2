package com.margomarket.security;

import com.margomarket.moderation.ModerationScope;
import com.margomarket.moderation.UserRestrictedException;
import com.margomarket.ratelimit.LoginAttemptGuard;
import com.margomarket.user.ChangeDisplayNameRequest;
import com.margomarket.user.ChangePasswordRequest;
import com.margomarket.user.RegisterRequest;
import com.margomarket.user.User;
import com.margomarket.user.UserMapper;
import com.margomarket.user.UserResponse;
import com.margomarket.user.UserService;
import com.margomarket.user.VerifyEmailRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Konto i logowanie", description = "Rejestracja, logowanie i ustawienia konta")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SecurityController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptGuard loginAttempts;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        loginAttempts.ensureAllowed(request.username(), ip);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException exception) {
            loginAttempts.recordFailure(request.username(), ip);
            throw exception;
        }
        loginAttempts.recordSuccess(request.username(), ip);
        User user = (User) userService.loadUserByUsername(request.username());
        if(user.isAccessBlocked()) throw new UserRestrictedException(ModerationScope.ACCOUNT,user.getAccountBlockReason(),user.getAccountBlockUntil());
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

    @PostMapping("/verify-email")
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        if (!userService.verifyEmail(request)) {
            throw new IllegalArgumentException("Kod jest błędny, wygasł lub przekroczono limit prób");
        }
    }

    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal User user) {
        return userMapper.toResponse(user);
    }

    @PutMapping("/me/display-name")
    public UserResponse changeDisplayName(@AuthenticationPrincipal User user,
                                          @Valid @RequestBody ChangeDisplayNameRequest request) {
        return userMapper.toResponse(userService.changeDisplayName(user.getId(), request));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal User user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.getId(), request);
    }
}
