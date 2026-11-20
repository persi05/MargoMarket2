package com.margomarket.security;

import com.margomarket.model.User;
import com.margomarket.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final JwtService jwtService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);
        String email;

        try {
            email = jwtService.extractUsername(token);
        } catch (RuntimeException exception) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = userService.getByEmail(email);
            if (jwtService.isTokenValid(token, user)) {
                String path=request.getRequestURI().substring(request.getContextPath().length());
                if(user.isAccessBlocked() && !path.equals("/api/auth/moderation") && !path.equals("/api/auth/login")) {
                    response.setStatus(403);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
                    mapper.writeValue(response.getWriter(),new com.margomarket.exception.UserRestrictedException(com.margomarket.service.ModerationScope.ACCOUNT,user.getAccountBlockReason(),user.getAccountBlockUntil()).response());
                    return;
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
